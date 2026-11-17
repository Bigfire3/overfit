# Vorschläge: einfachere Bedienung (16.11.2026)

Entstanden aus neun Audits (je UI-Bereich plus ein Durchgang durch die elf wichtigsten Abläufe), siehe DECISIONS
„Bewegung, ‚Jetzt‘-Farbe und Workout-Dock“. Die Audits lasen Code und Robolectric-Bilder; auf einem Gerät lief nichts.

Dieses Dokument enthält nur, was **nicht** umgesetzt ist, weil es Verhalten oder Daten ändert oder etwas entfernt, das
du so bestellt hast. Was ohne Entscheidung ging, ist im PR umgesetzt (Liste am Ende). Sag eine Nummer, dann baue ich sie.

Aufwand: **S** = Stunden, **M** = ein halber bis ganzer Tag, **L** = mehr.

## Die zehn mit dem größten Nutzen

| # | Vorschlag | Was es bringt | Aufwand |
|---|---|---|---|
| 1 | **Geändertes Gewicht gilt für die restlichen Sätze.** Wer in Satz 1 aus 60 kg 62,5 macht, bekommt die noch offenen Sätze derselben Übung, die noch den alten Wert haben, mit. Abgehakte und selbst geänderte Sätze bleiben. | Heute: 4 Sätze = 4× Feld antippen und tippen. Dann: 1×. Die größte Reibung im Training. | M |
| 2 | **Laufendes Training auf dem Workouts-Tab nur einmal zeigen.** Heute steht es dreimal da: Panel „Läuft gerade“, Fuß mit zwei Knöpfen, Mini-Leiste. Panel und Fuß weg, Mini-Leiste bleibt; beendet wird im Training. | Rund 250 dp mehr Liste, kein „welcher Knopf ist der richtige“. | S |
| 3 | **Gespeicherte Workouts eingeklappt öffnen.** Heute ist jede Übung aufgeklappt, ein Workout mit 6 Übungen sind 6 Bildschirme. Eingeklappt passen alle Übungen samt „Starten“ auf einen. Neue Workouts und Importe bleiben offen. | Überblick statt Scrollen; man will meist nur schauen und starten. | S |
| 4 | **Tastatur „Fertig“ im letzten Feld hakt den Satz ab.** Heute springt „Weiter“ ins Gewicht des nächsten Satzes; zum Abhaken: Tastatur schließen, Kreis treffen. | 2 Tipps weniger pro Satz mit geänderten Werten. | M |
| 5 | **Körpergewicht direkt in der Karte eintragen.** Tipp auf die Karte macht daraus das Zahlenfeld, „Fertig“ speichert. Heute: eigener Screen, tippen, zurück. | Ein Screenwechsel und ein Zurück weniger, täglich. | M |
| 6 | **Kalender: Wochen ohne Foto flach.** Jede Woche reserviert eine quadratische Fotokachel, auch ohne Foto; ein Monat ist fast schwarz. Wochen ohne Foto nur Zahl + Marken. | Etwa zwei Monate pro Bildschirm statt drei Wochen. | M |
| 7 | **±15 s gilt für die restlichen Sätze der Übung.** Heute nur für die laufende Pause, beim nächsten Satz ist die alte Zeit wieder da. Optional: beim Beenden in den Plan übernehmen. | Pause einmal passend stellen statt nach jedem Satz. | M |
| 8 | **Pausenzeit überall als „2:30“ mit −15/+15.** Heute im Profil „Standard-Pause (s) 150“, im Editor „150 s“, im Plan „2:30“: drei Schreibweisen, zweimal Tastatur und Kopfrechnen. | Einheitlich, ohne Tastatur. | M |
| 9 | **„+“ auf dem Workouts-Tab als Sheet statt eigener Seite;** „Workout erstellen“ öffnet gleich die Übungsauswahl. | Eine Seite und ein Tipp weniger beim Anlegen. | M |
| 10 | **Kopfzeile im Training entlasten:** „Übung hinzufügen“ und „Training abbrechen“ ins ⋮ (Abbrechen mit Doppeltipp), Name als Text statt als Feld (Umbenennen im ⋮ oder im Abschluss). Ob „Beenden“ oben bleibt, entscheidest du. | „Übung hinzufügen“ ist nicht mehr hinter allen Karten versteckt; kein versehentliches Öffnen der Tastatur neben „Minimieren“. | S |

## Weitere, nach Bereich

### Laufendes Training
- **11 · Notiz nur zeigen, wenn es eine gibt** (S): Das leere Notizfeld kostet in jeder Übung 52 dp. Vorschlag: vorhandene Notiz als graue Zeile unter dem Kopf, sonst ein „Notiz“-Eintrag im ⋮ der Übung.
- **12 · Ausgelassene Übung als eine Zeile** (S): heute Kopf plus Knopf über die volle Breite (136 dp); eine Zeile mit „Zurückholen“ rechts spart 70 dp. Ausgelassene könnten außerdem ans Ende der Liste rutschen (M).
- **13 · Haken-Knopf in der Mini-Leiste** (M): loggt den nächsten Satz von jedem Tab aus, ohne das Training zu öffnen.
- **14 · Umsortieren sichtbar machen** (M): „Nach oben / Nach unten“ im ⋮ der Übung (heute nur lange drücken auf den Kopf, das findet man nicht); beim Ziehen alle Karten einklappen.
- **15 · Pausenende zeigen** (S): Am Ende der Pause steht die Insel 1,5 s auf „Los“, bevor sie geht.

### Workouts-Tab und Editor
- **16 · Welches Workout ist dran?** (S): das am längsten nicht trainierte markieren oder nach oben setzen.
- **17 · Werte aus Satz 1 in leere Folgesätze** (M): neue Übung bringt 3 leere Sätze, 60 × 8 tippt man dreimal.
- **18 · Pause und Notiz im Editor kompakter** (S): „+ Satz“ und „Pause 2:30“ in einer Zeile, Notiz nur wenn gefüllt.
- **19 · Workouts auf dem Tab per Ziehen ordnen** (M): die Datenbank kennt die Reihenfolge schon.
- **20 · Hinweis aufs Wegwischen** (S): Dass man Sätze nach links wischt, steht nirgends; ein einmaliger Hinweis unter der ersten Karte.
- **21 · „Zuletzt vor 3 Tagen“ antippbar** (S): öffnet dieses Training, statt Kalender → Tag → Karte.

### Übungen
- **22 · „Eigene Übung“ als „+“ in die Kopfzeile** (S): Heute ein dauerhafter Hauptknopf für eine seltene Aktion; in der Auswahl steht dieselbe Aktion am Ende von 302 Ergebnissen.
- **23 · Übungsdetail speichert selbst** (M): Tracking-Art und Pause brauchen „Speichern“, das auch den Screen verlässt; Pläne speichern überall automatisch. Deine Entscheidung, weil die Tracking-Art beeinflusst, wie Sätze geloggt werden.
- **24 · Details aus der Auswahl mit „Hinzufügen“** (M): Heute Bild antippen → Details → zurück → Zeile antippen.
- **25 · Formular eigene Übung kürzen** (M): 51 Chips in drei Panels, nur der Name ist Pflicht. „Weitere Angaben“ aufklappbar.
- **26 · Verlaufskurve im Übungsdetail** (M): Der Trend steht nur in zehn Textzeilen.
- **27 · Eigene Übung ohne Bild** (S): heute ein bildschirmbreites schwarzes Quadrat mit zwei Buchstaben; 96 dp reichen.

### Kalender, Tag, Fotos
- **28 · Raster/Liste als Icon in die Kopfzeile** (S): liegt im ⋮ (2 Tipps); „Fotos vergleichen“ dafür in den Foto-Viewer.
- **29 · Foto-Viewer:** Zoom mit zwei Fingern und Doppeltipp, Runterziehen schließt, ein flacher Pager über alle Fotos statt zwei Achsen (M).
- **30 · Zwischen Tagen blättern** (M): Pfeile oder Wischen im Tages-Screen statt zurück und neu antippen.
- **31 · Leeres Gewichtsfeld löscht** (M): heute stellt Verlassen den alten Wert wieder her, gelöscht wird nur über den Mülleimer.
- **32 · Tag ohne Training:** Abschnitt „Trainings“ weglassen statt „An diesem Tag kein Training“ (S).
- **33 · Kamera- und Bibliothek-Kachel** (S): die Icons sind vertauscht (Bild für Kamera, Plus für Galerie); braucht ein Kamera-Icon. Eine „+“-Kachel mit Auswahl spart außerdem eine Zeile.
- **34 · Rangliste unter der Muskelkarte** (M): kleine Muskeln (Unterarme, Waden) trifft man kaum.

### Abschluss und Verlauf
- **35 · Abschluss: „Fertig“ statt „Speichern“, kein Zurück-Pfeil** (S): das Training ist da längst gespeichert, der Pfeil sieht nach „zurück ins Training“ aus. Coach-Karte nach oben.
- **36 · „Als Workout speichern“ ins ⋮** (S): steht als einzelnes Panel am Ende eines langen Scrolls.
- **37 · Foto entfernen mit Doppeltipp** (S): das kleine ✕ löscht sofort und endgültig, anders als alles andere.
- **38 · Teilen-Seite entschlacken** (S): vierzeiliger QR-Text drückt „Link kopieren“ unter den Knopf; der KI-Prompt steht unter der Workout-Liste und noch einmal im Import.

### Coach und Chat
- **39 · Modell wählen = übernehmen** (S): Tipp auf Haiku/Sonnet/Opus wählt und schließt; „Übernehmen“ nur beim eigenen Schlüssel.
- **40 · „Übernehmen“ prüft den eigenen Schlüssel** (M): heute wird auch ein falscher gespeichert, der Fehler kommt erst im Chat.
- **41 · Coach-Profil speichert selbst** (M): jede Chip-Änderung braucht „Speichern“ unter dem Falz; Schließen verwirft still.
- **42 · Vorschlag im Chat sendet direkt** (S): heute füllt er nur das Feld, es braucht einen zweiten Tipp. Deine Entscheidung wegen der Preisregel.
- **43 · Frühere Chats im selben Screen** (M): heute drei Ebenen (Tab → Liste → eigener Chat-Screen).
- **44 · Antworten kopierbar** (M): lange drücken kopiert.
- **45 · Vorschläge je nach Screen** (S): Über einem laufenden Training passen „Ersatz für die nächste Übung“, „Passt das Gewicht?“ besser als „Wie lief mein Training?“.
- **46 · Story:** Finger halten pausiert, letzte Seite bleibt stehen (S). Ändert das automatische Schließen.
- **47 · Coach-Anweisungen:** deine Ergänzung nach oben, die feste Grundanweisung als 3-Zeilen-Vorschau darunter (S).
- **48 · Auswertung je Übung kürzen** (M): dieselben Zahlen stehen dreimal (heute, letztes Mal, Differenz).

### Profil und Guthaben
- **49 · Profil neu gruppieren** (S): drei Abschnitte haben je eine Zeile. „Über dich“, „App“, „Training & Pause“ (Pause, Vibration, Ticken zusammen).
- **50 · KI-Zugang als eine Zeile** (S): „KI-Zugang“ und „Standardmodell“ öffnen denselben Screen.
- **51 · Guthaben-Seite:** Pakete, die man nicht kaufen kann („Bald im Store“), raus; bis zu fünf Kleingedruckt-Hinweise bündeln (S).
- **52 · Web-Pakete antippbar** (S): sehen wie Auswahl aus, sind es nicht.
- **53 · Neuer Wiederherstellungscode mit Doppeltipp** (S): ein Tipp macht den alten ungültig.
- **54 · Schlüssel einfügen-Knopf** (S): ein API-Schlüssel wird nie getippt.
- **55 · Anfrage-Protokoll zweizeilig** (S): doppelt so viele Einträge pro Bildschirm.

## In diesem PR schon umgesetzt (ohne Entscheidung möglich)
Siehe PROGRESS „Bewegung“ und die PR-Beschreibung.
