package de.spawnerauto;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoMod implements ClientModInitializer {

    public static AutoMod I;
    public final ModConfig cfg = new ModConfig();
    public String status = "Bereit";

    // "[HugoSMP] Your balance: $1,794,234.77"
    private static final Pattern BALANCE = Pattern.compile("Your balance:\\s*\\$\\s*([\\d.,]+)");
    private static final Pattern PRICE_D = Pattern.compile("\\$\\s*(\\d[\\d.,]*)\\s*([kKmMbB])?");
    private static final Pattern PRICE_W = Pattern.compile("(?i)(?:price|preis)\\D{0,12}(\\d[\\d.,]*)\\s*([kKmMbB])?");

    private enum Phase {
        IDLE, WAIT_SPAWNER_GUI, CLICK_DROP, CLOSE_SPAWNER, WAIT_PICKUP,
        NEXT_ITEM, WAIT_AH_GUI, AH_SORT, AH_SCAN, SELL, WAIT_SELL, PAY_WAIT_BAL
    }

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Phase phase = Phase.IDLE;
    private long tick = 0, wait = 0, deadline = 0;
    private long nextCycle = 0, nextPay = 0;

    private Map<Item, Integer> before = new HashMap<>();
    private final Map<Item, Integer> gained = new LinkedHashMap<>();
    private final Deque<Item> queue = new ArrayDeque<>();
    private Item cur;
    private int dropsDone, sortClicks, pages, stacksToSell;
    private double scanMax, scanMin;
    private String lastSig = "";
    private double perItem;
    private boolean holdDone, confirmTried;

    private boolean openGui;
    private boolean debug;
    private Screen lastDumpScreen;
    private long dumpAt;

    @Override
    public void onInitializeClient() {
        I = this;
        cfg.load();
        if (cfg.enabled) nextCycle = 1200;
        if (cfg.payEnabled) nextPay = 1200;

        ClientTickEvents.END_CLIENT_TICK.register(m -> tick());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("sauto")
                .then(ClientCommandManager.literal("gui").executes(c -> { openGui = true; return 1; }))
                .then(ClientCommandManager.literal("start").executes(c -> { setEnabled(true); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(c -> { setEnabled(false); return 1; }))
                .then(ClientCommandManager.literal("now").executes(c -> { runNow(); return 1; }))
                .then(ClientCommandManager.literal("spawner").executes(c -> { setSpawnerFromCrosshair(); return 1; }))
                .then(ClientCommandManager.literal("debug").executes(c -> {
                    debug = !debug;
                    msg("Debug " + (debug ? "AN: öffne das Auktionshaus, die Infos erscheinen im Chat." : "AUS"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(c -> {
                    msg(status + " | Automation " + (cfg.enabled ? "AN" : "AUS") + " | AFKPay " + (cfg.payEnabled ? "AN" : "AUS"));
                    return 1;
                }))
            ));
    }

    // ---------- Steuerung (auch von der GUI genutzt) ----------

    public void setEnabled(boolean on) {
        cfg.enabled = on;
        if (on) nextCycle = tick + 20;
        else if (phase != Phase.IDLE && phase != Phase.PAY_WAIT_BAL) { abort(); }
        cfg.save();
        msg("Automation " + (on ? "gestartet." : "gestoppt."));
    }

    public void setPay(boolean on) {
        cfg.payEnabled = on;
        if (on) nextPay = tick + 100;
        cfg.save();
        msg("AFKPay " + (on ? "AN" : "AUS"));
    }

    public void runNow() {
        if (!cfg.enabled) { msg("Erst starten: /sauto start"); return; }
        nextCycle = 0;
    }

    public void setSpawnerFromCrosshair() {
        if (mc.world != null && mc.crosshairTarget instanceof BlockHitResult b
            && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockPos p = b.getBlockPos();
            if (mc.world.getBlockState(p).isOf(Blocks.SPAWNER)) {
                cfg.sx = p.getX(); cfg.sy = p.getY(); cfg.sz = p.getZ(); cfg.hasSpawner = true; cfg.save();
                msg("Spawner gesetzt: " + p.getX() + " " + p.getY() + " " + p.getZ());
                return;
            }
        }
        msg("Schau direkt auf den Spawner-Block.");
    }

    public void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[SpawnerAuto] " + s), false);
    }

    // ---------- Hilfsfunktionen ----------

    private void set(Phase p, int delay, int timeout) {
        phase = p;
        wait = tick + delay;
        deadline = tick + timeout;
    }

    private void abort() {
        if (mc.currentScreen instanceof GenericContainerScreen && mc.player != null) mc.player.closeHandledScreen();
        phase = Phase.IDLE;
    }

    private void fail(String why) {
        abort();
        status = "Fehler: " + why;
        msg("Abbruch: " + why + " (neuer Versuch in 2 Min.)");
        nextCycle = tick + 2400;
    }

    private void finishCycle() {
        phase = Phase.IDLE;
        nextCycle = tick + cfg.cycleMin * 1200L;
        status = "Zyklus fertig, nächster in " + cfg.cycleMin + " Min.";
        msg(status);
    }

    private Map<Item, Integer> countInv() {
        Map<Item, Integer> m = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    private int countFullStacks(Item it) {
        int maxC = new ItemStack(it).getMaxCount();
        int n = 0;
        for (int i = 9; i <= 44; i++) {
            ItemStack s = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (s.isOf(it) && s.getCount() >= maxC) n++;
        }
        return n;
    }

    /** Sucht nur VOLLE Stacks (Anzahl == Maximalgröße). */
    private int findInvSlot(Item it) {
        int maxC = new ItemStack(it).getMaxCount();
        for (int i = 9; i <= 44; i++) {
            ItemStack s = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (s.isOf(it) && s.getCount() >= maxC) return i;
        }
        return -1;
    }

    private static int containerSize(GenericContainerScreenHandler h) {
        return h.slots.size() - 36;
    }

    private int findInContainer(GenericContainerScreenHandler h, Item it) {
        int n = containerSize(h);
        for (int i = 0; i < n; i++) if (h.getSlot(i).getStack().isOf(it)) return i;
        return -1;
    }

    private void click(GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private static double parseNum(String num, String suf) {
        String n = num.replace(",", "");
        if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
        try {
            double v = Double.parseDouble(n);
            if (suf != null && !suf.isEmpty()) {
                switch (Character.toLowerCase(suf.charAt(0))) {
                    case 'k' -> v *= 1e3;
                    case 'm' -> v *= 1e6;
                    case 'b' -> v *= 1e9;
                    default -> { }
                }
            }
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double listingPrice(ItemStack s) {
        LoreComponent lore = s.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        for (Text l : lore.lines()) {
            String t = l.getString();
            Matcher m = PRICE_D.matcher(t);
            if (m.find()) {
                double v = parseNum(m.group(1), m.group(2));
                if (v > 0) return v;
            }
            m = PRICE_W.matcher(t);
            if (m.find()) {
                double v = parseNum(m.group(1), m.group(2));
                if (v > 0) return v;
            }
        }
        return -1;
    }

    private static int colorOf(Text t) {
        if (t.getStyle().getColor() != null) return t.getStyle().getColor().getRgb();
        for (Text s : t.getSiblings()) {
            int c = colorOf(s);
            if (c != -1) return c;
        }
        return -1;
    }

    /** true = "Lowest price" ausgewählt, false = nicht ausgewählt, null = nicht feststellbar. */
    private Boolean sortIsLowest(ItemStack hopper) {
        LoreComponent lore = hopper.get(DataComponentTypes.LORE);
        if (lore == null) return null;
        List<Text> lines = new ArrayList<>();
        for (Text l : lore.lines()) if (!l.getString().isBlank()) lines.add(l);
        if (lines.isEmpty()) return null;

        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i).getString().toLowerCase();
            if (s.contains("lowest") || s.contains("niedrig") || s.contains("günstig")) { idx = i; break; }
        }
        if (idx == -1 && lines.size() >= 4) idx = 3; // "ist immer die vierte Option"
        if (idx == -1) return null;

        String t = lines.get(idx).getString().trim();
        if (!t.isEmpty() && "▶►»>→✔✓➤●◆■".indexOf(t.charAt(0)) >= 0) return true;

        Map<Integer, Integer> freq = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i == idx) continue;
            freq.merge(colorOf(lines.get(i)), 1, Integer::sum);
        }
        if (freq.isEmpty()) return null;
        int common = Collections.max(freq.entrySet(), Map.Entry.comparingByValue()).getKey();
        return colorOf(lines.get(idx)) != common;
    }

    // ---------- Tick / Zustandsautomat ----------

    private void tick() {
        tick++;
        if (openGui && mc.currentScreen == null) { openGui = false; mc.setScreen(new ControlScreen()); }
        if (debug) debugTick();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) {
            phase = Phase.IDLE;
            return;
        }
        if (phase != Phase.IDLE && tick > deadline) {
            if (phase == Phase.PAY_WAIT_BAL) { phase = Phase.IDLE; nextPay = tick + 1200; return; }
            fail("Zeitüberschreitung (" + phase + ")");
            return;
        }

        switch (phase) {
            case IDLE -> {
                if (mc.currentScreen != null) return;
                if (cfg.payEnabled && tick >= nextPay) {
                    mc.getNetworkHandler().sendChatCommand("bal");
                    set(Phase.PAY_WAIT_BAL, 0, 300);
                    return;
                }
                if (cfg.enabled && cfg.hasSpawner && tick >= nextCycle) startCycle();
            }
            case WAIT_SPAWNER_GUI -> {
                if (tick < wait) return;
                if (mc.currentScreen instanceof GenericContainerScreen gs) {
                    if (findInContainer(gs.getScreenHandler(), Items.DROPPER) < 0) {
                        fail("Drop-Button (Dropper) im Spawner-GUI nicht gefunden");
                        return;
                    }
                    set(Phase.CLICK_DROP, 10, 80);
                }
            }
            case CLICK_DROP -> {
                if (tick < wait) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen gs)) { fail("Spawner-GUI wurde geschlossen"); return; }
                int slot = findInContainer(gs.getScreenHandler(), Items.DROPPER);
                if (slot < 0) { fail("Drop-Button verschwunden"); return; }
                click(gs.getScreenHandler(), slot);
                dropsDone++;
                if (dropsDone < cfg.dropClicks) set(Phase.CLICK_DROP, 15, 80);
                else set(Phase.CLOSE_SPAWNER, 10, 80);
            }
            case CLOSE_SPAWNER -> {
                if (tick < wait) return;
                mc.player.closeHandledScreen();
                status = "Warte auf Item-Aufnahme...";
                set(Phase.WAIT_PICKUP, 80, 400);
            }
            case WAIT_PICKUP -> {
                if (tick < wait) return;
                queue.clear();
                Item sellItem = cfg.spawnerType == 0 ? Items.BONE : Items.GUNPOWDER;
                if (countFullStacks(sellItem) <= 0) {
                    msg("Keine vollen Stacks von " + new ItemStack(sellItem).getName().getString() + " im Inventar.");
                    finishCycle();
                    return;
                }
                queue.add(sellItem);
                set(Phase.NEXT_ITEM, 0, 200);
            }
            case NEXT_ITEM -> {
                if (queue.isEmpty()) { finishCycle(); return; }
                cur = queue.poll();
                stacksToSell = countFullStacks(cur); // ALLE vollen Stacks dieses Spawner-Items
                String name = (cfg.searchTerm == null || cfg.searchTerm.isBlank())
                    ? new ItemStack(cur).getName().getString()
                    : cfg.searchTerm.trim();
                status = "Suche im AH: " + name;
                mc.getNetworkHandler().sendChatCommand("ah " + name);
                sortClicks = 0; pages = 0; lastSig = ""; scanMax = -1; scanMin = -1;
                set(Phase.WAIT_AH_GUI, 0, 160);
            }
            case WAIT_AH_GUI -> {
                if (tick < wait) return;
                if (mc.currentScreen instanceof GenericContainerScreen) set(Phase.AH_SORT, 20, 400);
            }
            case AH_SORT -> {
                if (tick < wait) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen gs)) { skipItem("AH-GUI geschlossen"); return; }
                GenericContainerScreenHandler h = gs.getScreenHandler();
                int hop = findInContainer(h, Items.HOPPER);
                if (hop < 0) {
                    msg("Trichter (Sortierung) nicht gefunden - fahre ohne Sortier-Check fort.");
                    set(Phase.AH_SCAN, 0, 600);
                    return;
                }
                Boolean ok = sortIsLowest(h.getSlot(hop).getStack());
                if (ok == null) {
                    msg("Sortierung nicht lesbar (nutze /sauto debug) - fahre fort.");
                    set(Phase.AH_SCAN, 0, 600);
                } else if (ok) {
                    set(Phase.AH_SCAN, 5, 600);
                } else if (sortClicks < 6) {
                    sortClicks++;
                    click(h, hop);
                    set(Phase.AH_SORT, 14, 400);
                } else {
                    skipItem("'Lowest price' ließ sich nicht auswählen");
                }
            }
            case AH_SCAN -> {
                if (tick < wait) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen gs)) { skipItem("AH-GUI geschlossen"); return; }
                GenericContainerScreenHandler h = gs.getScreenHandler();
                int listing = containerSize(h) - 9; // untere Reihe = Buttons
                StringBuilder sig = new StringBuilder();
                for (int i = 0; i < listing; i++) {
                    ItemStack s = h.getSlot(i).getStack();
                    if (s.isEmpty() || !s.isOf(cur)) continue;
                    double price = listingPrice(s);
                    if (price <= 0) continue;
                    double perStack = price / s.getCount() * s.getMaxCount();
                    if (perStack < cfg.priceMin || perStack > cfg.priceMax) continue; // außerhalb Min/Max ignorieren
                    double per = price / s.getCount();
                    sig.append(price).append('|');
                    if (scanMax < 0 || per > scanMax) scanMax = per;
                    if (scanMin < 0 || per < scanMin) scanMin = per;
                }
                String sg = sig.toString();
                boolean stop = cfg.priceMode == 1
                    || sg.isEmpty()
                    || sg.equals(lastSig)
                    || pages + 1 >= cfg.maxPages;
                if (!stop) {
                    int hop = findInContainer(h, Items.HOPPER);
                    int next = hop >= 0 ? hop + 1 : containerSize(h) - 4;
                    if (next >= containerSize(h) || h.getSlot(next).getStack().isEmpty()) stop = true;
                    else {
                        lastSig = sg;
                        pages++;
                        click(h, next);
                        status = "AH Seite " + (pages + 1) + "...";
                        set(Phase.AH_SCAN, 10, 600);
                        return;
                    }
                }
                mc.player.closeHandledScreen();
                perItem = cfg.priceMode == 1 ? scanMin : scanMax;
                if (perItem <= 0) { skipItem("kein passender Preis im AH (zwischen Min- und Max-Preis)"); return; }
                status = "Preis/Item: " + (long) perItem;
                holdDone = false; confirmTried = false;
                set(Phase.SELL, 10, 200);
            }
            case SELL -> {
                if (tick < wait) return;
                if (stacksToSell <= 0) { set(Phase.NEXT_ITEM, 5, 200); return; }
                if (!holdDone) {
                    int slot = findInvSlot(cur);
                    if (slot < 0) { set(Phase.NEXT_ITEM, 5, 200); return; }
                    if (slot >= 36) mc.player.getInventory().setSelectedSlot(slot - 36);
                    else mc.interactionManager.clickSlot(0, slot, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
                    holdDone = true;
                    set(Phase.SELL, 5, 200);
                    return;
                }
                ItemStack held = mc.player.getMainHandStack();
                if (!held.isOf(cur) || held.getCount() < held.getMaxCount()) { holdDone = false; set(Phase.SELL, 5, 200); return; }
                long price = (long) Math.floor(perItem * held.getCount() * (100 - cfg.undercutPct) / 100.0);
                price = Math.max(Math.max(1, cfg.priceMin), Math.min(cfg.priceMax, price));
                mc.getNetworkHandler().sendChatCommand("ah sell " + price);
                status = "Verkaufe " + held.getName().getString() + " für " + price;
                stacksToSell--;
                holdDone = false; confirmTried = false;
                set(Phase.WAIT_SELL, 15, 200);
            }
            case WAIT_SELL -> {
                if (tick < wait) return;
                if (mc.currentScreen instanceof GenericContainerScreen gs && !confirmTried) {
                    GenericContainerScreenHandler h = gs.getScreenHandler();
                    for (int i = 0; i < containerSize(h); i++) {
                        String n = h.getSlot(i).getStack().getName().getString().toLowerCase();
                        if (n.contains("confirm") || n.contains("bestätig") || n.contains("accept")) { click(h, i); break; }
                    }
                    confirmTried = true;
                    set(Phase.WAIT_SELL, 15, 200);
                    return;
                }
                if (mc.currentScreen instanceof GenericContainerScreen) mc.player.closeHandledScreen();
                set(Phase.SELL, 20, 200);
            }
            case PAY_WAIT_BAL -> { }
        }
    }

    private void skipItem(String why) {
        if (mc.currentScreen instanceof GenericContainerScreen) mc.player.closeHandledScreen();
        msg("Überspringe " + (cur == null ? "Item" : new ItemStack(cur).getName().getString()) + ": " + why);
        set(Phase.NEXT_ITEM, 10, 200);
    }

    private void startCycle() {
        BlockPos p = new BlockPos(cfg.sx, cfg.sy, cfg.sz);
        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(p)) > 5.5) {
            fail("zu weit vom Spawner entfernt (max. ca. 5 Blöcke)");
            return;
        }
        status = "Öffne Spawner...";
        before = countInv();
        dropsDone = 0;
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(p), Direction.UP, p, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        set(Phase.WAIT_SPAWNER_GUI, 5, 120);
    }

    // ---------- AFKPay ----------

    private void onMessage(String text) {
        if (phase != Phase.PAY_WAIT_BAL) return;
        Matcher m = BALANCE.matcher(text);
        if (!m.find()) return;
        double balance;
        try {
            balance = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        long pay = (long) Math.floor(balance * cfg.payPercent / 100.0);
        phase = Phase.IDLE;
        nextPay = tick + cfg.payIntervalMin * 1200L;
        if (pay < 1 || pay < cfg.payMin) return;
        final long amount = pay;
        mc.execute(() -> {
            if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("pay " + cfg.payTarget + " " + amount);
        });
    }

    // ---------- Debug ----------

    private void debugTick() {
        Screen s = mc.currentScreen;
        if (!(s instanceof GenericContainerScreen gs)) { lastDumpScreen = null; return; }
        if (s != lastDumpScreen) { lastDumpScreen = s; dumpAt = tick + 15; return; }
        if (dumpAt == 0 || tick < dumpAt) return;
        dumpAt = 0;
        GenericContainerScreenHandler h = gs.getScreenHandler();
        int hop = findInContainer(h, Items.HOPPER);
        msg("--- Debug: Container " + containerSize(h) + " Slots, Trichter-Slot=" + hop);
        if (hop >= 0) dumpLore("Trichter", h.getSlot(hop).getStack());
        for (int i = 0; i < containerSize(h) - 9; i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (!st.isEmpty()) { dumpLore("Slot " + i + " (" + st.getName().getString() + " x" + st.getCount() + ")", st); break; }
        }
    }

    private void dumpLore(String title, ItemStack st) {
        msg(title + ":");
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) { msg("  (keine Lore)"); return; }
        for (Text l : lore.lines()) {
            int c = colorOf(l);
            msg("  [" + (c == -1 ? "-" : String.format("%06X", c)) + "] " + l.getString());
        }
    }
}
