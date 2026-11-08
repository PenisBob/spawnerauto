package de.spawnerauto;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ControlScreen extends Screen {

    private static int tab = 0; // 0 = Verkauf, 1 = AFKPay

    private final List<String> labelTexts = new ArrayList<>();
    private final List<Integer> labelYs = new ArrayList<>();

    private TextFieldWidget fCycle, fUndercut, fMin, fMax, fSearch;
    private TextFieldWidget fTarget, fPercent, fPayInt, fPayMin;

    public ControlScreen() {
        super(Text.literal("Spawner Auto"));
    }

    private ModConfig c() {
        return AutoMod.I.cfg;
    }

    private TextFieldWidget field(String label, int y, String value, String tip) {
        int lx = this.width / 2 - 120;
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, lx + 130, y, 110, 18, Text.literal(label));
        f.setMaxLength(40);
        f.setText(value);
        f.setTooltip(Tooltip.of(Text.literal(tip)));
        addDrawableChild(f);
        labelTexts.add(label);
        labelYs.add(y + 5);
        return f;
    }

    private ButtonWidget button(Text label, String tip, int x, int y, int w, ButtonWidget.PressAction action) {
        ButtonWidget b = ButtonWidget.builder(label, action)
            .dimensions(x, y, w, 20)
            .tooltip(Tooltip.of(Text.literal(tip)))
            .build();
        return addDrawableChild(b);
    }

    @Override
    protected void init() {
        labelTexts.clear();
        labelYs.clear();
        fCycle = fUndercut = fMin = fMax = fSearch = null;
        fTarget = fPercent = fPayInt = fPayMin = null;

        int lx = this.width / 2 - 120;
        int y = 24;

        button(autoLabel(), "Schaltet die komplette Automatisierung (Spawner leeren + verkaufen) an oder aus.",
            lx, y, 240, b -> {
                AutoMod.I.setEnabled(!c().enabled);
                b.setMessage(autoLabel());
            });
        y += 22;

        button(Text.literal(tab == 0 ? "» Verkauf «" : "Verkauf"), "Einstellungen für Spawner und Auktionshaus.",
            lx, y, 118, b -> { apply(); tab = 0; clearAndInit(); });
        button(Text.literal(tab == 1 ? "» AFKPay «" : "AFKPay"), "Einstellungen für die automatische Geld-Zahlung.",
            lx + 122, y, 118, b -> { apply(); tab = 1; clearAndInit(); });
        y += 26;

        if (tab == 0) {
            button(spawnerLabel(),
                "Skeleton-Spawner droppt Knochen, Creeper-Spawner Schwarzpulver. Verkauft werden alle vollen Stacks dieses Items im Inventar.",
                lx, y, 240, b -> {
                    c().spawnerType = c().spawnerType == 0 ? 1 : 0;
                    c().save();
                    b.setMessage(spawnerLabel());
                });
            y += 22;

            button(modeLabel(),
                "Teuerster: alle AH-Seiten durchblättern und den höchsten Preis nehmen. Günstigster: nur Seite 1 (niedrigster Preis).",
                lx, y, 240, b -> {
                    c().priceMode = c().priceMode == 0 ? 1 : 0;
                    c().save();
                    b.setMessage(modeLabel());
                });
            y += 26;

            fCycle = field("Zyklus alle (Min)", y, String.valueOf(c().cycleMin),
                "Alle wie viele Minuten der Spawner geleert und verkauft wird."); y += 21;
            fUndercut = field("Preis-Abzug (%)", y, String.valueOf(c().undercutPct),
                "Wie viel Prozent unter dem ermittelten AH-Preis verkauft wird. 0 = genau der AH-Preis."); y += 21;
            fMin = field("Min-Preis pro Stack", y, fmtMoney(c().priceMin),
                "Niedrigster Preis, zu dem ein Stack verkauft wird. AH-Angebote darunter werden ignoriert. Erlaubt: 1k, 10m, 5000"); y += 21;
            fMax = field("Max-Preis pro Stack", y, fmtMoney(c().priceMax),
                "Höchster Preis, zu dem ein Stack verkauft wird. AH-Angebote darüber werden ignoriert. Erlaubt: 1k, 10m, 5000"); y += 21;
            fSearch = field("Suchbegriff (leer=auto)", y, c().searchTerm == null ? "" : c().searchTerm,
                "Was nach /ah eingegeben wird. Leer = Itemname des Spiels. Falls der Server englische Namen braucht: bone bzw. gunpowder."); y += 25;

            button(Text.literal("Spawner setzen"), "Schau direkt auf den Spawner-Block (max. ca. 5 Blöcke Abstand) und klicke hier.",
                lx, y, 118, b -> AutoMod.I.setSpawnerFromCrosshair());
            button(Text.literal("Jetzt starten"), "Startet sofort einen Durchlauf (Automation muss AN sein).",
                lx + 122, y, 118, b -> { apply(); AutoMod.I.runNow(); });
            y += 22;
        } else {
            button(payLabel(), "Zahlt regelmäßig einen Teil des Geldes dieses Accounts an das Pay-Ziel.",
                lx, y, 240, b -> {
                    AutoMod.I.setPay(!c().payEnabled);
                    b.setMessage(payLabel());
                });
            y += 26;

            fTarget = field("Pay-Ziel (Spieler)", y, c().payTarget,
                "Name des Spielers, der das Geld bekommt (dein Hauptaccount)."); y += 21;
            fPercent = field("Anteil vom Geld (%)", y, String.valueOf(c().payPercent),
                "Wie viel Prozent des aktuellen Guthabens gezahlt werden. 50 = die Hälfte."); y += 21;
            fPayInt = field("Zahlen alle (Min)", y, String.valueOf(c().payIntervalMin),
                "Alle wie viele Minuten gezahlt wird."); y += 21;
            fPayMin = field("Mindestbetrag ($)", y, fmtMoney(c().payMin),
                "Unter diesem Betrag wird nichts gezahlt. Erlaubt: 1k, 10m, 5000"); y += 25;
        }

        button(Text.literal("Speichern & Schließen"), "Speichert alle Werte und schließt das Fenster.",
            lx, y, 240, b -> close());
    }

    private Text autoLabel() {
        return Text.literal("Automation: " + (c().enabled ? "AN" : "AUS"));
    }

    private Text payLabel() {
        return Text.literal("AFKPay: " + (c().payEnabled ? "AN" : "AUS"));
    }

    private Text spawnerLabel() {
        return Text.literal("Spawner: " + (c().spawnerType == 0 ? "Skeleton (Knochen)" : "Creeper (Schwarzpulver)"));
    }

    private Text modeLabel() {
        return Text.literal("Preis: " + (c().priceMode == 0 ? "teuerster (alle Seiten)" : "günstigster (Seite 1)"));
    }

    private static int toInt(String s, int d, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    /** Akzeptiert 5000, 1k, 10m, 2.5m, 1,000 */
    static long parseMoney(String s, long d) {
        s = s.trim().toLowerCase().replace(",", "").replace("$", "").replace(" ", "");
        if (s.isEmpty()) return d;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') { mult = 1e3; s = s.substring(0, s.length() - 1); }
        else if (last == 'm') { mult = 1e6; s = s.substring(0, s.length() - 1); }
        else if (last == 'b') { mult = 1e9; s = s.substring(0, s.length() - 1); }
        try {
            long v = (long) (Double.parseDouble(s) * mult);
            return Math.max(0, v);
        } catch (NumberFormatException e) {
            return d;
        }
    }

    static String fmtMoney(long v) {
        if (v >= 1_000_000_000L && v % 1_000_000_000L == 0) return (v / 1_000_000_000L) + "b";
        if (v >= 1_000_000L && v % 1_000_000L == 0) return (v / 1_000_000L) + "m";
        if (v >= 1_000L && v % 1_000L == 0) return (v / 1_000L) + "k";
        return String.valueOf(v);
    }

    private void apply() {
        ModConfig c = c();
        if (fCycle != null) c.cycleMin = toInt(fCycle.getText(), c.cycleMin, 1, 1440);
        if (fUndercut != null) c.undercutPct = toInt(fUndercut.getText(), c.undercutPct, 0, 99);
        if (fMin != null) c.priceMin = parseMoney(fMin.getText(), c.priceMin);
        if (fMax != null) c.priceMax = parseMoney(fMax.getText(), c.priceMax);
        if (fSearch != null) c.searchTerm = fSearch.getText().trim();
        if (c.priceMax < c.priceMin) c.priceMax = c.priceMin;
        if (fTarget != null) {
            String t = fTarget.getText().trim();
            if (!t.isEmpty()) c.payTarget = t;
        }
        if (fPercent != null) c.payPercent = toInt(fPercent.getText(), c.payPercent, 1, 100);
        if (fPayInt != null) c.payIntervalMin = toInt(fPayInt.getText(), c.payIntervalMin, 1, 1440);
        if (fPayMin != null) c.payMin = parseMoney(fPayMin.getText(), c.payMin);
        c.save();
    }

    @Override
    public void close() {
        apply();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int lx = cx - 120;
        // Farben mit Alpha (0xFF......), sonst sind Texte in neueren Minecraft-Versionen unsichtbar
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 8, 0xFFFFFFFF);
        for (int i = 0; i < labelTexts.size(); i++) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(labelTexts.get(i)), lx, labelYs.get(i), 0xFFE0E0E0);
        }
        String sp = c().hasSpawner ? ("Spawner: " + c().sx + " " + c().sy + " " + c().sz) : "Spawner: nicht gesetzt";
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sp + " | " + AutoMod.I.status), cx, this.height - 12, 0xFFA0A0A0);
    }
}
