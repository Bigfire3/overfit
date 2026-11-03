# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt ist heute nur das Guthaben (mit Modellwahl) mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`); dazu der Web-Kauf mit Guthaben-Codes **im Testmodus** (Paddle-Sandbox, Session Z); alles andere hier ist geplant.

## Grundsätze
- **Grundfunktionen kostenlos, keine Werbung.** Training loggen, Katalog, Workouts, Pausentimer, Auswertung, Diary, Teilen, eigener API-Key bleiben gratis.
- Bezahlt wird nur, was Kosten verursacht (KI über das Guthaben) oder späterer Mehrwert (Pro).
- Texte versprechen „kein Abo nötig“, nie „nie ein Abo“.

## Guthaben (heute)
- Coach-Guthaben in Euro („Guthaben“, EN „Credit“; früher „Fuel“). Kauf **in der App** über Google Play Billing bzw. Apple In-App-Kauf. Small-Business-Gebühr der Stores: **15 %**, im Backend als `STORE_FEE_BP` eingepreist (Gutschrift = Preis × (1 − Gebühr) / (1 + Aufschlag)). Die Store-Produkt-IDs `overfit.fuel.5` / `.10` bleiben (bei den Stores registriert, nicht sichtbar).
- Apple verlangt, dass es das Guthaben **parallel als In-App-Kauf** gibt, sobald die App auf externe Kaufwege verweist.

### Modelle und Preise (Session Y: echte Namen, keine Stufen)
Es gibt keine Marketing-Namen wie „Standard“, „Pro“ oder „Lite“. Die App zeigt die Modelle, die wirklich antworten, mit Anbieter, und was sie
kosten. Abgerechnet wird je Anfrage nach Token zum Listenpreis des Modells plus 8 % Aufschlag (`MARKUP_BP`). Der Nutzer wählt das Modell
(Coach-Einstellungen → „Modell für deinen Coach“); „Mit KI ausfüllen“ nutzt immer das kleine Modell.

| Modell (Anbieter) | Listenpreis je 1 Mio. Token (ein / aus) | Coach-Auswertung* | Chat-Nachricht* | „Mit KI ausfüllen“* |
|---|---|---|---|---|
| Gemini 3.6 Flash (Google), Voreinstellung | 0,75 $ / 3,75 $ (ab 1.1.2027: 1,50 $ / 7,50 $) | ≈ 1,5 ct (ab 2027 ≈ 3,0 ct) | ≈ 1,1 ct | – |
| Claude Sonnet 5.5 (Anthropic) | 2 $ / 10 $ | ≈ 3,0 ct | ≈ 1,8 ct | – |
| Llama 3.1 8B (Cloudflare Workers AI) | 0,28 $ / 0,83 $ | – | – | ≈ 0,04 ct (nur dieses Modell) |

\* berechnet, nicht geraten: Listenpreis × typische Token-Menge × 1,08, bei 0,92 €/$. Typische Mengen aus den echten Prompts
(Coach 8 200 ein / 1 400 aus, Chat 6 700 / 500, Ausfüllen 900 / 120; Gemini zählt ca. 1 000 Thinking-Token als Ausgabe dazu),
Methode in DECISIONS.md „Kostenschätzung“. Unter jeder Antwort steht der echte Preis; die Schätzung ist nur Orientierung vor der Wahl.

### Marge (offen)
- **Aufschlag 8 %** auf den Listenpreis der Anbieter, auf jede Anfrage (Selbstkosten × 1,08 wird berechnet).
- **Store-Kauf:** Store nimmt 15 %. Von 10 € Kaufpreis bleiben 8,50 €, daraus entstehen 7,87 € Guthaben (÷ 1,08). Nach Anbieterkosten
  bleiben ≈ 0,63 € = **≈ 6,3 % vom Kaufpreis** (0,85 × 8/108), bevor Zahlungs-/Betriebskosten des Backends (Cloudflare, klein) dazukommen.
  Im Web (ohne Store-Gebühr) entsprechend mehr Guthaben für den gleichen Preis oder mehr Marge.
- **Verfall:** Startguthaben (Spot) verfällt nach 180 Tagen, gekauftes Guthaben nie.
- **Ausweichung:** Antwortet ein anderes Modell als das gewählte, zahlt der Nutzer höchstens den Preis des gewählten; ist das Ersatzmodell
  teurer, trägt die Marge die Differenz (sie kann dann negativ sein). Die App sagt dem Nutzer, welches Modell geantwortet hat.
- **Auswertung für den Betreiber:** `GET /v1/admin/usage` (docs/backend-api.md) zeigt je Modell Anbieterkosten, Berechnetes und Marge, ohne Inhalte.
- Preise pflegt der Betreiber in `backend/src/providers.ts`.

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
- **Marge beim Web-Kauf (offen, Geldentscheidung, Vorschlag unten):** siehe „Rechnung Web-Kauf“.
- Backend-Vertrag: `docs/backend-api.md`, Abschnitt „Web-Kauf und Guthaben-Codes“.

### Rechnung Web-Kauf (Session Z, mit der Marge aus Session Y)
Annahmen: Paddle 5 % + 0,50 € vom Bruttopreis, USt DE 19 % (Paddle führt sie als Merchant of Record ab), Guthaben 1:1 = Preis,
Guthaben voll verbraucht zum Listenpreis × 1,08. „Ergebnis“ = Auszahlung − Anbieterkosten.

| Paket | USt | Paddle | Auszahlung | Anbieterkosten (Guthaben ÷ 1,08) | Ergebnis | in % vom Preis |
|---|---|---|---|---|---|---|
| 5 € | 0,80 € | 0,75 € | 3,45 € | 4,63 € | **−1,18 €** | −23,6 % |
| 10 € | 1,60 € | 1,00 € | 7,40 € | 9,26 € | **−1,86 €** | −18,6 % |
| 20 € | 3,19 € | 1,50 € | 15,31 € | 18,52 € | **−3,21 €** | −16,0 % |

Die Gebühren (USt + Paddle, 31 % bei 5 €, 26 % bei 10 €, 23 % bei 20 €) liegen weit über der Marge von 8 % Aufschlag
(≈ 6,3 % vom Kaufpreis). **1:1-Guthaben verliert also bei jedem Paket Geld.** Hinweis: Auch die Store-Rechnung oben lässt die USt
weg (Stores sind in der EU ebenfalls Verkäufer und führen sie ab): 10 € → 8,40 € netto → nach 15 % 7,14 € Auszahlung bei 7,29 €
Anbieterkosten, also knapp negativ.

**Vorschlag (fair, kein versteckter Aufschlag): Aufteilung offen zeigen, Mindestpaket 10 €.**
- Die Kaufseite zeigt je Paket den Preis und darunter, wohin er geht: „10 € = 7,40 € Guthaben + 1,60 € USt + 1,00 € Zahlungsgebühr
  (Paddle)“. Guthaben = Preis − USt − Paddle-Gebühr. Die Marge bleibt allein der sichtbare Aufschlag von 8 % auf die Modellpreise,
  wie überall in der App. Nichts wird versteckt, jeder Euro ist erklärt.
- Pakete **10 €, 20 €, 50 €** (kein 5 €: dort gingen 31 % an USt und Gebühr, 3,45 € Guthaben wirken unfair; ab 10 € sinkt der
  Anteil auf 26 %, bei 50 € auf 20 %). Guthaben-Codes nennen den Guthabenwert (z. B. „7,40 € Guthaben“).
- Gleiche Logik für den Store: Gutschrift = (Preis − USt) × (1 − Store-Gebühr), ohne den zweiten ÷ 1,08 (heute zieht die Store-Formel
  den Aufschlag doppelt ab: einmal bei der Gutschrift, einmal bei jeder Anfrage).
- Verworfen: Aufschlag pauschal auf ≈ 35–45 % anheben (versteckt die Gebühr in jeder Anfrage und trifft auch Store-Käufer);
  Gebühr obendrauf („10 € Guthaben für 13,29 €“) ist ehrlich, wirkt aber teurer als dieselbe Aufteilung von unten.
- **Umsetzung:** heute nicht geändert, weil Fabian 1:1 vorgegeben hat und im Testmodus kein echtes Geld fließt. Vor dem Live-Start
  entscheiden; technisch ist es eine Formel in `webPackages()` (`backend/src/web.ts`) plus die Aufteilung auf der Kaufseite.

## Später
1. **Pro-Abo** (optional): Duo, erweiterte Statistiken, verschlüsseltes Cloud-Backup/Sync, mehr Coach-Gedächtnis, etwas Guthaben inklusive.
2. **Programme** zum Kaufen, auch von Trainern mit Umsatzbeteiligung.
3. **Trainer-/Studio-Version.**
4. **Einmaliger Unterstützer-Kauf.**

## Offene Punkte für Fabian
- Gewerbe anmelden, Umsatzsteuer (Kleinunternehmer vs. Regelbesteuerung), Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung (digitale Inhalte), Paddle-Live-Konto, Store-Konten (Small-Business-Programme beantragen), juristische Prüfung der externen Kaufwege.
- Aufschlag für das 1:1-Web-Guthaben festlegen (siehe „Marge“ oben).
