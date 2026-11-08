# SpawnerAuto (Fabric 1.21.11, clientseitig)

Ablauf pro Zyklus (alle 15 Minuten, nur VOLLE Stacks werden verkauft):
1. Spawner (Rechtsklick) öffnen, Dropper-Button klicken, GUI schließen, Items aufsammeln
2. Für jede neue Item-Sorte: /ah <Itemname>, Trichter prüfen ("Lowest price" = 4. Option),
   Preise lesen (teuerster = alle Seiten durchblättern, oder günstigster = Seite 1)
3. Stack in die Hand nehmen und /ah sell <Preis>
4. AFKPay: /bal lesen, /pay <Ziel> <Anteil>

Befehle: /sauto gui | start | stop | now | spawner | status | debug
Stell dich direkt an den Spawner (max. ca. 5 Blöcke), schau ihn an und nutze "/sauto spawner".
