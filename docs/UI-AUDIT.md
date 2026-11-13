# UI/UX-Audit (12.11.2026)

Werkzeuge: Skill **impeccable** (`audit.native`, `polish`, `harden`) und die Jakub-Krehel-Skills **better-interface /
better-ui / better-accessibility** (vom Branch `claude/install-impeccable-jakub-skills-8tqz25` gelesen, dort noch nicht
gemergt). Der Skill „make-interfaces-feel-better“ ist in diesem Paket nicht enthalten; seine Themen (Mikro-Interaktionen,
konzentrische Radien, optische Ausrichtung, Hit-Areas) decken `better-ui` und `better-accessibility` ab.

Umfang: Designsystem (`ui/theme`, `ui/components`), Tab-Leiste, Training-Tab, laufendes Training mit Pausentimer,
Trainingsabschluss, Katalog, Kalender, Coach (Archiv, Kontext-Sheet, Guthaben-Popup), Chat-Posteingang, Import,
Einstellungen. Geprüft aus dem Code plus Robolectric-Screenshots (`docs/screenshots/ui-polish/`). Nicht geprüft: iOS auf
Gerät (nur kompiliert), echte TalkBack/VoiceOver-Sitzung.

Kontext festgehalten in `PRODUCT.md` und `DESIGN.md` (neu). Bewusste Entscheidungen aus `docs/DECISIONS.md` (z. B.
Icon-Druck 0,88, Karten 0,98, Feder beim Loslassen, keine Rahmen, Doppeltipp) sind **keine** Befunde.

## Audit Health Score (vorher → nachher)

| # | Dimension | Vorher | Nachher | Wichtigster Befund |
|---|-----------|-------:|--------:|--------------------|
| 1 | Accessibility | 2 | 3 | Text-Kontrast unter 4,5 : 1 (faint, muted im Panel, Rot im Menü), Tippflächen < 48 dp |
| 2 | Performance | 3 | 3 | – (Listen in LazyColumn, Bilder über Coil) |
| 3 | Appearance & Theming | 3 | 4 | Platzhalterfarbe uneinheitlich; Radien nicht konzentrisch |
| 4 | Platform Conformance | 3 | 3 | Eigenes Designsystem (gewollt), Insets/Zurück/IME sauber |
| 5 | Adaptivity | 3 | 3 | 360 dp und 1,3× Schrift abgesichert; Tablet/Quer kein Ziel |
| **Total** | | **14/20** | **16/20** | Good |

**Plattform-Urteil:** besteht. Die App ist bewusst kein Material/HIG-Look, behandelt aber Insets, System-Zurück,
Tastatur, Haptik und Bedienungshilfen-Rollen korrekt; kein „Web-Port“.

## Befunde nach Priorität

### P0 (blockierend)
Keine. Kein Ablauf ist blockiert, kein Datenverlust-Pfad gefunden.

### P1 (vor Release beheben) – alle behoben
| Befund | Ort | Vorher | Nachher |
|---|---|---|---|
| Text in `faint` fast unlesbar | `Theme.kt` `faint`; genutzt in Kalender (leere Tage, Wochentage), Übungsdetail („Trainiert auch“, Equipment), Katalog-Anzahl, alle Platzhalter | #48484A = **2,3 : 1** auf Schwarz | #757579 = 4,6 : 1 auf Schwarz, 3,7 : 1 auf `surface` |
| Grauer Text auf heller Fläche im Panel | `muted` auf `surfacePressed` (Chips/Segmente/SmallButton im Panel) | **4,4 : 1** | `muted` #98989D = 4,9 : 1 (7,3 : 1 auf Schwarz) |
| Roter Menütext „Entfernen“ | `MoreMenu` (`Blocks.kt`), `negative` auf `surfacePressed` | #FF453A = **4,1 : 1** | `negative` #FF6961 = 4,9 : 1; `armed` (Icon-Rot) bleibt |
| Weißer Text auf Rot beim Satz-Wegwischen | `SetRowSurface` | Weiß auf #FF453A = **3,4 : 1** | Fläche `armedFill` = 5 : 1 |
| Tippflächen unter 48 dp | Tab-Leiste (44 dp, Padding außerhalb), Countdown im Pausentimer (32 dp), Segmente (42 dp), Foto-× (32 dp), Kontext-Chip-× (36 dp), Guthaben-Schließen (40 dp) | 32–44 dp | 44–60 dp, Optik unverändert |
| Fehler werden verschluckt | Backup-Export (`App.kt`), Abschluss speichern (`FinishScreen`, blieb bei „…“ hängen) | nichts passiert / Knopf hängt | Hinweis mit Weg zur Behebung; Zurück verlässt nach Fehler trotzdem |
| Posteingang zeigt „Nichts Neues.“ beim Laden und offline | `ChatInboxScreens.kt` | falsche Aussage | „Wird geladen …“ bzw. Fehlertext + „Erneut versuchen“ |

### P2 (nächster Durchgang) – behoben
| Befund | Ort | Nachher |
|---|---|---|
| Timer-Ziffern springen beim Zählen | Pausentimer (klein), Trainingsdauer | Tabellenziffern für Hero/Title/Number und laufende Werte (`Theme.type.tabular`). Erster Versuch „alle Stile“ schnitt „Überspringen“ auf 360 dp ab (Sonnet-Gegencheck), deshalb gezielt |
| Radien nicht konzentrisch | `Block` 16 dp um Satzzeile 12 dp bei 8 dp Abstand, Feld 10 dp | Block 20 dp (12 + 8), Feld 8 dp (12 − 4) |
| Pausentimer springt beim Auf-/Zuklappen | `RestBar` | weiche Größenanimation ohne Nachfedern; „Nächste“ groß zweizeilig |
| ⋮-Menü erscheint hart, kein Druck-Feedback | `MoreMenu` | wächst aus der ⋮-Ecke (150 ms, Scale 0,96 → 1 + Fade), gedrückter Eintrag heller |
| Such-× ohne Druck-Feedback | `SearchInput` | `pressScale` wie alle Icon-Knöpfe |
| Platzhalter uneinheitlich | `MultilineInput` (muted) vs. Rest (faint) | überall `faint` |
| Leerer Training-Tab ohne nächsten Schritt | `Tabs.kt` | Hauptknopf „Neues Workout“ im Leerzustand, Text ohne „oben auf +“ |
| Katalog leer mit aktivem Filter | `Catalog.kt` | „Filter zurücksetzen“ |
| Coach-Kontext: Suche ohne Treffer sagt „Noch keine Übung trainiert.“ | `ContextSheet.kt` | „Keine trainierte Übung passt zur Suche.“ |
| QR-Kurzcode offline = „kein Overfit-Inhalt“ | `ImportViewModel` | eigener Fehler `NETWORK` mit Hinweis auf Verbindung |
| Guthaben nicht erreichbar, kein Retry | `CreditPopup` | „Erneut versuchen“ |
| Coach-Archiv: „Noch keine Auswertungen“ blitzt vor dem Laden | `CoachHomeScreen` | erst nach Antwort der Datenbank |
| Gelöschte Auswertung zeigt ewig „Coach schaut …“ | `CoachReviewScreen` | „Diese Auswertung gibt es nicht mehr.“ |
| Plan übernehmen/ablehnen meldet Erfolg vor dem Schreiben | `CoachScreens.kt` | Meldung erst nach Erfolg, sonst Fehlertext; Plan-Diff-Fehler beendet „…“ |

### P2 – offen (bewusst nicht in diesem PR)
- Foto-Vorbereitung schlägt fehl → Foto wird still verworfen (`App.kt` `onAddPhoto`, `addDayPhoto`). Braucht einen
  Fehlerkanal in Tag-, Detail- und Abschluss-Screen.
- Guthaben-Chip zeigt bei Fehler nur „– €“; Fuel-Screen blendet Aufladen bei Konfigurationsfehler aus; Einstellungszeile
  „Guthaben“ leer bei Fehler.
- Preis-Schätzung im Chat: bei Fehler nur Pfeil statt „Preis unbekannt“ (`CoachChatTab.kt`).
- Chat-Verbindungen zeigen „Noch nicht verbunden“ vor dem ersten Laden (`ChatScreens.kt`).
- Teilen-Screen bleibt leer, wenn das Workout gelöscht wurde; Foto-Teilen ohne Fehlermeldung.
- Übungsdetail, Trainingsdetail und Bearbeiten rendern während des Ladens nichts (nicht einmal den Zurück-Kopf).
- Gekürzte Chat-Titel/Anhang-Labels ohne Weg zum vollen Text.

### P3 (Feinschliff, nicht umgesetzt)
- Linien-Icons haben immer 1,5 px Strich; neben fettem Knopftext wirken sie dünner (better-ui „stroke tracks text weight“).
- `pressScale` federt beim Loslassen nach (better-ui: bounce 0) – bewusst so entschieden (DECISIONS „Knöpfe“).
- Menü-Ursprung ist in RTL nicht gespiegelt (es gibt keine RTL-Sprache).

## Positive Befunde
- Durchgängiges Token-System, keine harten Farben in Screens; `NoBordersTest` und `ButtonContrastTest` schützen es.
- Bestätigungen per Doppeltipp mit Screenreader-Ansage, rot/grün nach Bedeutung, nie nur Farbe.
- Ein Übergang für alles (Fade-through), Taps während des Wechsels geschluckt.
- Viele Screens nutzen bereits null = laden (`StartScreen`, `Catalog`, `DayScreen`, `CoachHistoryScreen`).
- Knopftexte schrumpfen statt abgeschnitten zu werden; `ListRow` bricht statt mitten im Wort zu trennen.

## Absicherung
- `ContrastTest` (neu): muted/faint/negative/positive auf allen Flächen, Weiß auf Wisch-Rot, Zahlenfelder.
- Bestehende UI-Tests (Robolectric) und Screenshot-Tests laufen weiter; Belege `docs/screenshots/ui-polish/`.
