# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt sind das Coach-Guthaben (früher „Fuel“, mit Preisstufen) mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`) und der Web-Kauf mit Guthaben-Codes **im Testmodus** (Paddle-Sandbox, Session Z); alles andere hier ist geplant.

## Grundsätze
- **Grundfunktionen kostenlos, keine Werbung.** Training loggen, Katalog, Workouts, Pausentimer, Auswertung, Diary, Teilen, eigener API-Key bleiben gratis.
- Bezahlt wird nur, was Kosten verursacht (KI über Fuel) oder späterer Mehrwert (Pro).
- Texte versprechen „kein Abo nötig“, nie „nie ein Abo“.

## Fuel (heute)
- Coach-Guthaben in Euro. Kauf **in der App** über Google Play Billing bzw. Apple In-App-Kauf. Small-Business-Gebühr der Stores: **15 %**, im Backend als `STORE_FEE_BP` eingepreist (Gutschrift = Preis × (1 − Gebühr) / (1 + Aufschlag)).
- Apple verlangt, dass es Fuel **parallel als In-App-Kauf** gibt, sobald die App auf externe Kaufwege verweist.

### Preisstufen (Session X)
Abgerechnet wird je Anfrage nach Token zum Listenpreis des Modells plus 8 % Aufschlag (`MARKUP_BP`). Die Stufe wählt der Nutzer
(Coach-Einstellungen → „Coach-Qualität“), „Mit KI ausfüllen“ nutzt immer `lite`.

| Stufe | Modell | Listenpreis je 1 Mio. Token (ein / aus) | Typische Coach-Auswertung* |
|---|---|---|---|
| Standard (Voreinstellung) | Gemini 3.6 Flash | 0,75 $ / 3,75 $ (ab 1.1.2027: 1,50 $ / 7,50 $) | ≈ 1,5 ct (ab 2027 ≈ 3 ct) |
| Pro | Claude Sonnet 5.5 | 2 $ / 10 $ | ≈ 4 ct |
| Lite (nur Übung ausfüllen) | Llama 3.1 8B (Workers AI) | 0,28 $ / 0,83 $ | – (eine Übung ausfüllen ≈ 0,07 ct) |

\* grob: 10 000 Token Eingabe, 2 000 Ausgabe (bei Gemini inkl. Thinking), mit Aufschlag, 0,92 €/$.
Fällt der Anbieter einer Stufe aus, antwortet die andere; der Nutzer zahlt nie mehr als die gewählte Stufe (Differenz trägt die Marge).
Preise pflegt der Betreiber in `backend/src/providers.ts`.

## Guthaben im Web (Session Z, Testmodus)
- Kaufseite `https://bigfire3.github.io/overfit/guthaben/`, Pakete **5 €, 10 €, 20 €**. Die Preise sind **Endpreise inkl. USt**,
  das Guthaben entspricht dem Betrag: **5 € zahlen = 5 € Guthaben**, ohne Abzug. Das ist ehrlicher als die Store-Formel
  (Preis × (1 − Gebühr) / (1 + Aufschlag)); die Marge steckt allein in den Modellpreisen (`MARKUP_BP`), die die App nennt.
- **Kauf ohne Login:** „Im Web aufladen“ in der App holt einen kurzlebigen, einmaligen Kauflink (15 min) zur anonymen Konto-ID und
  öffnet ihn im Browser. Zahlung über **Paddle als Merchant of Record** (übernimmt EU-USt, Beleg, Erstattungen). Ein Webhook bucht
  das Guthaben idempotent; Erstattung/Chargeback holt es zurück (nie unter 0) und markiert den Vorfall.
- **Guthaben-Codes:** ohne App auf derselben Seite kaufbar (Geschenk), einmalig in der App einlösbar („Code einlösen“).
- **Store-Regeln:** Der App-Knopf ist per `/v1/config` → `webPurchase.channels` schaltbar. Standard: nur in seitlich installierten
  APKs (GitHub), in Store-Builds aus, bis die Rechtslage geprüft ist (EU: DMA-Programme der Stores, USA: externe Links nach den
  Epic-Urteilen; Apple verlangt dann parallel den In-App-Kauf). „Code einlösen“ bleibt überall (Codes kauft man außerhalb der App).
- **Vor dem Live-Start (Fabian):** Paddle-Live-Konto mit Domain- und Kontoprüfung, Impressum, Datenschutz, AGB/Widerruf (die Seite
  zeigt Platzhalter „folgt vor dem Start“), juristische Prüfung, dann Repo-Variable `PADDLE_ENV = live` und Live-Keys.
- **Marge (offen, Geldentscheidung):** Paddle nimmt 5 % + 0,50 € je Kauf, die USt (DE 19 %) geht ab. Von 10 € bleiben ≈ 7,40 €,
  von 5 € ≈ 3,45 €, von 20 € ≈ 15,30 €. Mit 1:1-Guthaben trägt sich das erst bei einem Aufschlag von ≈ 35 % (10 €) bis ≈ 45 % (5 €);
  heute sind es 8 %. Vorschlag vor dem Live-Start: `MARKUP_BP` auf ≈ 3500–4000 und die Store-Gutschrift auf Preis × (1 − Store-Gebühr)
  ohne zweiten Aufschlag umstellen. Im Testmodus kostet das nichts (Sandbox, kein echtes Geld). Siehe DECISIONS.md.
- Backend-Vertrag: `docs/backend-api.md`, Abschnitt „Web-Kauf und Guthaben-Codes“.

## Später
1. **Pro-Abo** (optional): Duo, erweiterte Statistiken, verschlüsseltes Cloud-Backup/Sync, mehr Coach-Gedächtnis, etwas Fuel inklusive.
2. **Programme** zum Kaufen, auch von Trainern mit Umsatzbeteiligung.
3. **Trainer-/Studio-Version.**
4. **Einmaliger Unterstützer-Kauf.**

## Offene Punkte für Fabian
- Gewerbe anmelden, Umsatzsteuer (Kleinunternehmer vs. Regelbesteuerung), Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung (digitale Inhalte), Paddle-Live-Konto, Store-Konten (Small-Business-Programme beantragen), juristische Prüfung der externen Kaufwege.
- Aufschlag für das 1:1-Web-Guthaben festlegen (siehe „Marge“ oben).
