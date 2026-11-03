# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 0b | Kotlin Multiplatform, Schwarz-Weiß-Design, DE/EN, Onboarding, eigener API-Key, iOS-Hülle | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | erledigt |
| 2 | Workout-Modus, Pausentimer | offen |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | offen |
| 4 | KI-Coach | offen |
| 5 | Feinschliff | offen |
| 6 | Store-Release (App Store, Play Store) | offen |
| 7 | Credits-System (Coach-Guthaben) | Backend + API-Vertrag fertig; App-Anbindung (Fuel, Demo) fertig; Store-Kauf offen (braucht Konten) |
| 8 | Trainings teilen (Link, Share-Sheet, QR, Import) | Vorlagen erledigt (Session B), abgeschlossene Trainings folgen mit Phase 3 |
| 9 | Einladen per QR mit Startguthaben (NFC später als Spielerei) | App-Seite fertig (QR, Einlösen, Reveal); Server-Deployment und Attestierung offen |

## Nacht 3.11. – Fuel & Spot in der App (Session D)
Fertig:
- Geräte-Identität (Keystore / Secure Enclave), signierte Anfragen nach `docs/backend-api.md`, `FuelApi` (Ktor) mit Re-Registrierung und Zeitkorrektur, Fehlercodes
- Profil → **Fuel**: Saldo („Fuel · 4,20 €“), verfallendes Guthaben, Pakete 5/10 € („Bald im Store“), Buchungen, Wiederherstellungscode (erzeugen, kopieren, auf neuem Handy eingeben)
- **Spot**: „Freund spotten“ (Name, QR mit rotierendem Token, Haarlinien-Fortschritt), „Ich wurde gespottet“ (Android: QR scannen oder Link einfügen; iOS: Kamera-App öffnet den Link), Reveal mit Haptik
- **Demo** (ohne Server): Profil → Fuel → „Demo ansehen“; Backend-URL per CI-Variable `OVERFIT_BACKEND_URL`, versteckter Override (7× Version in „Über“)
- Fuel als Option im KI-Zugang (`FuelProvider`); Tests: SHA/Signatur/Testvektor, Beträge, Reveal, Fuel-Client (MockEngine), QR, UI-Durchlauf Demo

### Von Fabian zu tun (Fuel/Spot)
1. Backend deployen (siehe oben), dann GitHub → Settings → Secrets and variables → **Variables** → `OVERFIT_BACKEND_URL` = Worker-URL anlegen. Ohne sie zeigt die App „startet bald“ + Demo.
2. Play-/Apple-Konten für echte Käufe (Play Billing / StoreKit) und Geräteprüfung (Play Integrity Cloud-Projekt, App Attest); Server-Prüfer ergänzen (Tokenformat siehe DECISIONS).
3. Optional: Web-Seite für Spot-Links ohne installierte App (QR enthält `overfit://spot#…`).

### Bitte auf dem Handy testen (Fuel/Spot)
1. Profil → „Fuel · startet bald“ öffnen → Text „Fuel startet bald“ → **Demo ansehen**
2. Demo: „Fuel · 4,20 €“, Pakete mit „Bald im Store“, Buchungen (Verbrauch, Aufladung, Spot), Wiederherstellungscode erzeugen + kopieren
3. **Freund spotten:** QR erscheint, Haarlinie läuft ab, alle ~30 s neuer Code; Namen eintragen
4. **Ich wurde gespottet:** beliebigen Link/Code einfügen → Einlösen → Bildschirm schwarz, zwei Herzschlag-Pulse, Zahl zählt 0,00 → 1,00 €, „Spot von Fabian“, kräftiger Puls; Tippen schließt
5. „Demo beenden“ → Fuel zeigt wieder „startet bald“. Version in „Über“ 7× tippen → Abschnitt „Entwickler“ mit Backend-URL
6. Profil → KI verbinden: erste Zeile „Fuel“ wählbar

## Phase 8: Vorlagen teilen und importieren (Session B)
- Format `docs/share-format.md`: JSON → Raw Deflate → Base64url im Fragment (`https://bigfire3.github.io/overfit/t#…`,
  `overfit://t#…`), Übungen per Katalog-ID, Name (DE/EN, unscharf aufgelöst) oder eigene Übung; Version und `kind`
  (später `workout`) werden geprüft
- Teilen: Vorlage öffnen → „Teilen“ → QR-Code, „Link teilen“ (System-Share-Sheet), „Link kopieren“, „Als Datei teilen“
  (`.overfit`); QR nur bis 1.000 Zeichen
- Import mit Vorschau (Name, Übungen, Sätze, was neu angelegt wird) aus Link, `.overfit`-Datei, QR-Scan (Android) und
  Zwischenablage (Link, Base64, JSON, auch im ```json-Block mit Text drumherum)
- „KI-Vorlage kopieren“: Prompt in App-Sprache für ChatGPT/Claude/Gemini; Antwort danach aus der Zwischenablage importieren
- Beispielvorlagen Push/Pull/Legs (je 6 Übungen, Katalog-IDs): im leeren Vorlagen-Zustand auf Start, in Übungen →
  Vorlagen und im Profil „Beispielvorlagen hinzufügen“ (idempotent)
- „Duo · bald“ auf dem Startbildschirm → Teaser-Screen
- Tests: Deflate, Format, Parser, Mapper, AI-Prompt (Common); QR gegen ZXing; Samples laden, Teilen→Import
  ergibt dieselbe Vorlage, Import-Flows in der UI (Robolectric)
- iOS: kein In-App-QR-Scanner (Kamera-App öffnet den `overfit://`-Link), Begründung in DECISIONS.md

**Von Fabian zu tun (Phase 8):**
1. Web-Fallback `https://bigfire3.github.io/overfit/t` muss live sein (GitHub Pages, aus PR #20), sonst öffnen
   https-Links ohne App im Leeren. Die App öffnet unabhängig davon `overfit://`-Links und `.overfit`-Dateien.
2. iOS Universal Links (https-Link öffnet direkt die App) brauchen eine Domain, die dir gehört, plus Associated Domains
   und `apple-app-site-association`. Später mit eigener Domain; bis dahin: `overfit://`-Link und Web-Seite mit Knopf.

## Nacht 3.11. – Backend Fuel/Spot + Web-Fallback (Session C)
Fertig (kein App-Code angefasst):
- `backend/`: Cloudflare Worker + D1, 42 Tests (Vitest/Miniflare, lokal ohne Cloudflare-Konto): anonyme Geräte-Identität
  (ECDSA P-256, signierte Anfragen mit Nonce/Zeitstempel), Konto mit Saldo/Buchungen in Mikro-Euro, Wiederherstellungscode,
  `POST /v1/messages` als Anthropic-Proxy (atomare Reservierung, Abrechnung nach `usage` + Aufschlag, Freigabe bei Fehlern),
  Kauf-Verifikation (idempotent, Paket-Gutschrift = Preis × (1 − Store) / (1 + Aufschlag)), Spot (Token, Challenge, Einlösen,
  Limits, Budget, Verfall, Belohnung bei der ersten Coach-Anfrage), Rate-Limits, einheitliches Fehlerformat
- API-Vertrag mit exaktem Signatur-Schema: `docs/backend-api.md` (Grundlage für die App-Session)
- `web/`: Fallback-Seite `t/index.html` (liest `#payload` laut `docs/share-format.md`, DE/EN, „In Overfit öffnen“, APK-Download)
  und Startseite; Playwright-Rauchtest; Workflow `pages.yml` zum Veröffentlichen; `backend.yml` testet bei Änderungen
- DECISIONS: „Produktentscheidungen Nacht 3.11.“ + Backend-Entscheidungen

Offen: Echte Google-Play-/Apple-Prüfer für Käufe und Play Integrity/App Attest (Schnittstellen + Mocks fertig, Produktion
antwortet bis dahin 503 `purchases_unavailable` / `attestation_unavailable`); App-Anbindung (`LlmProvider` „Fuel“, Spot-UI) nach `docs/backend-api.md`.

### Von Fabian zu tun
1. **GitHub Pages aktivieren:** Repo → Settings → Pages → Source „GitHub Actions“. Danach läuft `pages.yml` bei jedem Merge
   mit Änderungen in `web/` (oder manuell: Actions → „Web veröffentlichen“ → Run workflow). Ergebnis: https://bigfire3.github.io/overfit/
2. **Cloudflare-Backend deployen** (Schritte und alle Secrets in `backend/README.md`): Konto, `wrangler login`, `wrangler d1 create overfit`
   (ID in `wrangler.jsonc`), Migrationen, `ANTHROPIC_API_KEY` und `SPOT_SIGNING_KEY` als Secrets, `npm run deploy`. Worker-URL
   an die Session für die App-Anbindung geben.
3. Später für Käufe/Spot in Produktion: Play-Console- und Apple-Developer-Konten (Phase 6), Dienstkonto/Schlüssel als Secrets
   (Liste in `backend/README.md`), danach die echten Prüfer in `backend/src/verifiers.ts` ergänzen.
4. Für Android App Links / iOS Universal Links wird eine eigene Domain mit `/.well-known/` gebraucht (unter
   `github.io/overfit/` nicht möglich) – bis dahin öffnet der Button „In Overfit öffnen“.

### Bitte auf dem Handy testen (Web-Fallback, nach Aktivieren von Pages)
1. https://bigfire3.github.io/overfit/ öffnen: schlichte Startseite, Button „Für Android herunterladen“ führt zum Release „latest“
2. Einen Teilen-Link aus der App (Session B) im Handy-Browser öffnen, z. B. `https://bigfire3.github.io/overfit/t#<payload>`:
   Vorlage erscheint schwarz-weiß lesbar (Name, Übungen, Sätze, Pausen); Telefonsprache Deutsch → deutsche Texte
3. „In Overfit öffnen“ antippen → die App öffnet sich (sobald der `overfit://`-Import aus Session B drin ist)
4. Link ohne `#…` oder mit abgeschnittenem Payload öffnen → freundliche Fehlermeldung, kein Absturz

## Letzte Session
Phase 1 abgeschlossen (erst nativ gebaut, dann auf KMP portiert):
- Import-Skript `tools/catalog/import_catalog.py`: Workout Guide und free-exercise-db, an Commits gepinnt →
  1098 Übungen + 302 Workout-Guide-SVGs in den Compose Resources (offline, Android und iOS)
- Room KMP (Schema v1 unter `shared/schemas`): Übungen, Vorlagen, Vorlagen-Übungen, geplante Sätze. Katalog wird beim
  Start eingespielt, eigene Einstellungen an Übungen bleiben bei Katalog-Updates erhalten
- Tab „Übungen“ mit Umschalter Katalog/Vorlagen:
  - Katalog mit unscharfer Suche (auch deutsche Begriffe wie „Klimmzug“) und Filter-Chips (Muskelgruppe,
    Equipment, Eigene)
  - Detail mit Bild und Muskeln; Erfassung und Pause auch bei Katalogübungen änderbar
- Eigene Übungen anlegen, bearbeiten, löschen (Initialen-Kachel ohne Bild). Steckt eine Übung noch in einer Vorlage,
  wird sie nur ausgeblendet
- Vorlagen-Editor: Name, Übungen auswählen (Mehrfachauswahl), Sätze mit Zielwerten passend zur Erfassung
  (kg/lb nach Profil, „±“ für Unterstützung), Pause und Notiz je Übung, ersetzen, verschieben, entfernen, löschen.
  Ungespeichert verlassen nur mit zweitem Zurück
- Vorlagen auch auf dem Startbildschirm; Profil → „Alle Bilder offline laden“; „Alle Daten löschen“ leert auch die
  Datenbank
- Tests: Suche, Zahlen/Einheiten und Vorlagen-Entwurf als Common-Tests (Android-Host und iOS-Simulator); Datenbank,
  Seeding, Migration und UI-Durchlauf (Suche → eigene Übung → Vorlage) als Robolectric-Tests

## Davor: Signatur über Secrets und Planung Teilen/Guthaben (PR #14)
- Neuer Signaturschlüssel nur noch in GitHub-Secrets, `signing/` entfernt; CI baut zusätzlich das App Bundle für Google Play
- Apache-2.0-Lizenz, Skript zum Bereinigen der Git-Historie
- SPEC: Teilen per Link/QR, Coach-Guthaben, Einladen mit Startguthaben (Phasen 7–9)
- Lokal grün: `:shared:testAndroidHostTest`, `:app:testDebugUnitTest`, signiertes APK + AAB (neues Zertifikat geprüft)

Von dir zu tun:
1. Nach dem Merge `scripts/purge-old-signing-history.sh` ausführen (braucht `pip install git-filter-repo`,
   ggf. Force-Push auf main kurz erlauben), wenn keine anderen offenen PRs mehr laufen
2. Bundle-ID / Paketnamen `app.overfit` im Apple-Developer-Konto und in der Play Console reservieren

## Davor: Phase 0b
Phase 0b abgeschlossen:
- Umbau auf Kotlin Multiplatform: `:shared` (UI + Logik für Android und iOS), `:app` (Android-Hülle, unverändert signiert),
  `iosApp/` (XcodeGen-Spec + SwiftUI-Host), iOS-CI auf macOS
- Designsystem schwarz-weiß (Inter, keine Akzentfarbe, Haarlinien, Pill-Button, eigene Linien-Icons), Dunkel/Hell/System
- Deutsch und Englisch, in der App umschaltbar
- Onboarding: Name, Alter, Gewicht (kg/lb), KI-Zugang mit Key-Prüfung (OpenRouter, Anthropic, OpenAI) oder „Später einrichten“
- Profil-Tab mit Sprache, Darstellung, Profil, KI-Zugang, Coach-Gedächtnis, Über, Alle Daten löschen
- API-Key verschlüsselt im Android Keystore bzw. iOS Keychain
- Profil-Block für den Coach (`buildProfileBlock`), unit-getestet
- Tests: 19 Common-Tests + 2 Robolectric-UI-Tests, lokal grün; iOS-Code kompiliert (Linux), Linken/Starten prüft die iOS-CI

Offen / Hinweise:
- Drag & Drop zum Umsortieren kommt mit Phase 2 (jetzt: „Nach oben/unten“)
- „Training starten“ bleibt deaktiviert (Phase 2); Tippen auf eine Vorlage öffnet den Editor
- Globale Standard-Pause in den Einstellungen folgt in Phase 2 (leere Pause = Standard)
- iOS: Linken und Starten prüft weiterhin nur die iOS-CI

## Bitte auf dem Handy testen
1. Falls noch nicht geschehen: einmalig die alte Overfit-App deinstallieren (neuer Signaturschlüssel aus #14), dann
   `overfit.apk` aus Release „latest“ installieren und das Onboarding durchlaufen
2. Tab „Übungen“: Katalog erscheint nach kurzem Einrichten, Liste mit Strichgrafiken (Workout Guide, schwarz-weiß)
   und Fotos (free-exercise-db, nur mit Internet)
3. Suchen: „Klimmzug“, „Bankdrücken“, „Kniebeuge“, „pushup“, „bensh press“ (Tippfehler), „Brust“; Chips für
   Muskelgruppe, Equipment und „Eigene“ ausprobieren
4. Übung antippen → Detail; bei Pull-up Erfassung „Gewicht × Wdh.“ wählen, Pause 120 → Speichern; erneut öffnen →
   Werte sind da
5. „Eigene Übung“: z. B. „Handstand“, Erfassung „Zeit“ → erscheint mit Kachel „HA“; bearbeiten; löschen (zweimal tippen)
6. Umschalter „Vorlagen“ → „Neue Vorlage“: Name, „Übung hinzufügen“ (mehrere auswählen), Sätze füllen (Komma,
   „±“), Satz hinzufügen/entfernen, Übung ersetzen/verschieben/entfernen, Speichern → Vorlage in der Liste und auf Start
7. Im Editor etwas ändern und Zurück → Hinweis „Nicht gespeichert …“, zweites Zurück verwirft
8. Profil → Einheit auf lb → Vorlage öffnen: Gewichte in lb; zurück auf kg
9. Profil → „Alle Bilder offline laden“ (im WLAN) → Fortschritt; danach im Flugmodus Fotos weiter sichtbar
10. App schließen und neu öffnen → Vorlagen und eigene Übungen sind noch da; Sprache auf English → Übungen-Tab englisch

## Bitte auf dem Handy testen: Teilen und Import (Phase 8)
1. Start: Bei leerer Vorlagenliste „Beispielvorlagen hinzufügen“ → Push, Pull, Legs erscheinen; nochmal tippen (Profil
   → Vorlagen) → „schon da“, nichts doppelt
2. Vorlage Push öffnen → „Teilen“: QR-Code, „Link teilen“ → WhatsApp/Notizen/Mail; der Text enthält einen Link
3. QR-Code mit einem zweiten Handy (Kamera-App) scannen → Browser/Web-Seite zeigt die Vorlage; dort „In Overfit
   öffnen“ bzw. Link `overfit://t#…` antippen → Overfit öffnet die Vorschau
4. Übungen → Vorlagen → „Vorlage importieren“ → „QR-Code scannen“ (Android, Kamera-Erlaubnis) mit dem QR eines anderen
   Handys oder Bildschirms → Vorschau → „Übernehmen“ → Editor mit der neuen Vorlage
5. Link in die Zwischenablage kopieren (z. B. aus WhatsApp) → „Aus Zwischenablage importieren“
6. „KI-Vorlage kopieren“ → in ChatGPT/Claude/Gemini einfügen, Ziel/Equipment beantworten, Antwort („```json“-Block)
   kopieren → „Aus Zwischenablage importieren“ → Vorschau zeigt Übungen (neu = eigene Übung) → „Übernehmen“
7. Vorlage → „Teilen“ → „Als Datei teilen“ → `.overfit` an sich selbst mailen/in Dateien speichern, antippen → Overfit
   öffnet die Vorschau
8. Startbildschirm ganz unten: „Duo · bald“ → Erklärtext
9. Zwischenablage leer oder Text ohne Training → verständliche Fehlermeldung
