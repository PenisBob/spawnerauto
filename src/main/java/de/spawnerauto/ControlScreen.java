package de.spawnerauto;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ControlScreen extends Screen {

    private TextFieldWidget fCycle, fUndercut, fTarget, fPercent, fPayInt, fMin;
    private ButtonWidget bAuto, bPay, bMode;

    public ControlScreen() {
        super(Text.literal("Spawner Auto"));
    }

    private ModConfig c() {
        return AutoMod.I.cfg;
    }

    private TextFieldWidget field(int x, int y, int w, String value) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(""));
        f.setMaxLength(32);
        f.setText(value);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = Math.max(20, this.height / 2 - 128);
        int lx = cx - 105;
        int fx = cx + 25;

        bAuto = addDrawableChild(ButtonWidget.builder(autoLabel(), b -> {
            AutoMod.I.setEnabled(!c().enabled);
            b.setMessage(autoLabel());
        }).dimensions(lx, y, 210, 20).build());
        y += 24;

        bPay = addDrawableChild(ButtonWidget.builder(payLabel(), b -> {
            AutoMod.I.setPay(!c().payEnabled);
            b.setMessage(payLabel());
        }).dimensions(lx, y, 210, 20).build());
        y += 24;

        bMode = addDrawableChild(ButtonWidget.builder(modeLabel(), b -> {
            c().priceMode = c().priceMode == 0 ? 1 : 0;
            c().save();
            b.setMessage(modeLabel());
        }).dimensions(lx, y, 210, 20).build());
        y += 28;

        fCycle = field(fx, y, 80, String.valueOf(c().cycleMin)); y += 22;
        fUndercut = field(fx, y, 80, String.valueOf(c().undercutPct)); y += 22;
        fTarget = field(fx, y, 80, c().payTarget); y += 22;
        fPercent = field(fx, y, 80, String.valueOf(c().payPercent)); y += 22;
        fPayInt = field(fx, y, 80, String.valueOf(c().payIntervalMin)); y += 22;
        fMin = field(fx, y, 80, String.valueOf(c().payMin)); y += 28;

        addDrawableChild(ButtonWidget.builder(Text.literal("Spawner = Block im Fadenkreuz"), b -> {
            AutoMod.I.setSpawnerFromCrosshair();
        }).dimensions(lx, y, 210, 20).build());
        y += 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Zyklus jetzt starten"), b -> {
            apply();
            AutoMod.I.runNow();
        }).dimensions(lx, y, 102, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Speichern & Schließen"), b -> {
            apply();
            close();
        }).dimensions(lx + 108, y, 102, 20).build());
    }

    private Text autoLabel() {
        return Text.literal("Automation: " + (c().enabled ? "AN" : "AUS"));
    }

    private Text payLabel() {
        return Text.literal("AFKPay: " + (c().payEnabled ? "AN" : "AUS"));
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

    private void apply() {
        ModConfig c = c();
        c.cycleMin = toInt(fCycle.getText(), c.cycleMin, 1, 1440);
        c.undercutPct = toInt(fUndercut.getText(), c.undercutPct, 0, 99);
        String t = fTarget.getText().trim();
        if (!t.isEmpty()) c.payTarget = t;
        c.payPercent = toInt(fPercent.getText(), c.payPercent, 1, 100);
        c.payIntervalMin = toInt(fPayInt.getText(), c.payIntervalMin, 1, 1440);
        c.payMin = toInt(fMin.getText(), (int) c.payMin, 0, Integer.MAX_VALUE);
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
        int lx = cx - 105;
        int y = Math.max(20, this.height / 2 - 128);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, y - 14, 0xFFFFFF);

        int ty = y + 24 * 3 + 4;
        String[] labels = {"Zyklus alle (Min)", "Preis-Abzug (%)", "Pay-Ziel", "Pay-Anteil (%)", "Pay alle (Min)", "Pay-Minimum ($)"};
        for (int i = 0; i < labels.length; i++) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(labels[i]), lx, ty + i * 22 + 5, 0xE0E0E0);
        }
        String sp = c().hasSpawner ? ("Spawner: " + c().sx + " " + c().sy + " " + c().sz) : "Spawner: nicht gesetzt";
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sp + " | " + AutoMod.I.status), cx, this.height - 14, 0xA0A0A0);
    }
}
