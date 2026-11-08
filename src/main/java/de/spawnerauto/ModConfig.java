package de.spawnerauto;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ModConfig {
    public boolean enabled = false;
    public boolean hasSpawner = false;
    public int sx, sy, sz;

    public int cycleMin = 15;       // Zyklus-Intervall in Minuten
    public int dropClicks = 1;      // wie oft der Drop-Button geklickt wird
    public int undercutPct = 0;     // Abzug vom ermittelten Preis in %
    public int priceMode = 0;       // 0 = teuerster Preis (alle Seiten), 1 = günstigster Preis (erste Seite)
    public int maxPages = 25;       // max. Seiten beim Durchblättern
    public int spawnerType = 0;     // 0 = Skeleton (Knochen), 1 = Creeper (Schwarzpulver)
    public long priceMin = 1000;    // Mindestpreis pro Stack
    public long priceMax = 10_000_000; // Höchstpreis pro Stack
    public String searchTerm = "";  // Suchbegriff fürs AH (leer = Itemname des Clients)

    public boolean payEnabled = false;
    public String payTarget = "XhiroTTV";
    public int payPercent = 50;
    public int payIntervalMin = 30;
    public long payMin = 1000;

    private Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spawnerauto.properties");
    }

    private static int i(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k, String.valueOf(d))); } catch (NumberFormatException e) { return d; }
    }

    private static long l(Properties p, String k, long d) {
        try { return Long.parseLong(p.getProperty(k, String.valueOf(d))); } catch (NumberFormatException e) { return d; }
    }

    public void load() {
        if (!Files.exists(path())) return;
        try (Reader r = Files.newBufferedReader(path())) {
            Properties p = new Properties();
            p.load(r);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
            hasSpawner = Boolean.parseBoolean(p.getProperty("hasSpawner", "false"));
            sx = i(p, "sx", 0); sy = i(p, "sy", 0); sz = i(p, "sz", 0);
            cycleMin = i(p, "cycleMin", 15);
            dropClicks = i(p, "dropClicks", 1);
            undercutPct = i(p, "undercutPct", 0);
            priceMode = i(p, "priceMode", 0);
            maxPages = i(p, "maxPages", 25);
            spawnerType = i(p, "spawnerType", 0);
            priceMin = l(p, "priceMin", 1000);
            priceMax = l(p, "priceMax", 10_000_000);
            searchTerm = p.getProperty("searchTerm", "");
            payEnabled = Boolean.parseBoolean(p.getProperty("payEnabled", "false"));
            payTarget = p.getProperty("payTarget", "XhiroTTV");
            payPercent = i(p, "payPercent", 50);
            payIntervalMin = i(p, "payIntervalMin", 30);
            payMin = i(p, "payMin", 1000);
        } catch (IOException ignored) {
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("hasSpawner", String.valueOf(hasSpawner));
        p.setProperty("sx", String.valueOf(sx));
        p.setProperty("sy", String.valueOf(sy));
        p.setProperty("sz", String.valueOf(sz));
        p.setProperty("cycleMin", String.valueOf(cycleMin));
        p.setProperty("dropClicks", String.valueOf(dropClicks));
        p.setProperty("undercutPct", String.valueOf(undercutPct));
        p.setProperty("priceMode", String.valueOf(priceMode));
        p.setProperty("maxPages", String.valueOf(maxPages));
        p.setProperty("spawnerType", String.valueOf(spawnerType));
        p.setProperty("priceMin", String.valueOf(priceMin));
        p.setProperty("priceMax", String.valueOf(priceMax));
        p.setProperty("searchTerm", searchTerm);
        p.setProperty("payEnabled", String.valueOf(payEnabled));
        p.setProperty("payTarget", payTarget);
        p.setProperty("payPercent", String.valueOf(payPercent));
        p.setProperty("payIntervalMin", String.valueOf(payIntervalMin));
        p.setProperty("payMin", String.valueOf(payMin));
        try (Writer w = Files.newBufferedWriter(path())) {
            p.store(w, "Spawner Auto");
        } catch (IOException ignored) {
        }
    }
}
