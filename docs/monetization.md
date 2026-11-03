# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt ist heute das Guthaben (mit Modellwahl, 1:1 mit einem Aufschlag von 69 %) mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`); dazu der Web-Kauf mit Guthaben-Codes **im Testmodus** (Paddle-Sandbox, Session Z); alles andere hier ist geplant.

## Grundsätze
- **Grundfunktionen kostenlos, keine Werbung.** Training loggen, Katalog, Workouts, Pausentimer, Auswertung, Diary, Teilen, eigener API-Key bleiben gratis.
- Bezahlt wird nur, was Kosten verursacht (KI über das Guthaben) oder späterer Mehrwert (Pro).
- Texte versprechen „kein Abo nötig“, nie „nie ein Abo“.

## Guthaben (heute)
- Coach-Guthaben in Euro („Guthaben“, EN „Credit“; früher „Fuel“). Kauf **in der App** über Google Play Billing bzw. Apple In-App-Kauf, oder im Web über Paddle. **Guthaben = Kaufpreis, 1:1 in jedem Kanal** (Fabians Entscheidung „1:1 mit höherer Marge“): 10 € bezahlt sind 10 € Guthaben. USt, Zahlungs-/Store-Gebühr, Spot-Gutschriften und Betrieb trägt allein der **Aufschlag auf die Modellpreise** (`MARKUP_BP`), siehe „Rechnung“. Die Store-Produkt-IDs `overfit.fuel.5` / `.10` bleiben (bei den Stores registriert, nicht sichtbar).
- Apple verlangt, dass es das Guthaben **parallel als In-App-Kauf** gibt, sobald die App auf externe Kaufwege verweist.

### Modelle und Preise (Session Y: echte Namen, keine Stufen; Session AA: Aufschlag 69 %)
Es gibt keine Marketing-Namen wie „Standard“, „Pro“ oder „Lite“. Die App zeigt die Modelle, die wirklich antworten, mit Anbieter, und was sie
kosten. Abgerechnet wird je Anfrage nach Token zum Listenpreis des Modells plus **69 % Aufschlag** (`MARKUP_BP = 6900`, ein einziger Wert für alle Modelle).
Der Nutzer wählt das Modell (Coach-Einstellungen → „Modell für deinen Coach“); „Mit KI ausfüllen“ nutzt immer das kleine Modell.

| Modell (Anbieter) | Listenpreis je 1 Mio. Token (ein / aus) | Coach-Auswertung* | Chat-Nachricht* | „Mit KI ausfüllen“* |
|---|---|---|---|---|
| Gemini 3.6 Flash (Google), Voreinstellung | 0,75 $ / 3,75 $ (ab 1.1.2027: 1,50 $ / 7,50 $) | ≈ 2,4 ct (ab 2027 ≈ 4,7 ct) | ≈ 1,7 ct | – |
| Claude Sonnet 5.5 (Anthropic) | 2 $ / 10 $ | ≈ 4,7 ct | ≈ 2,9 ct | – |
| Llama 3.1 8B (Cloudflare Workers AI) | 0,28 $ / 0,83 $ | – | – | ≈ 0,05 ct (nur dieses Modell) |

\* berechnet, nicht geraten: Listenpreis × typische Token-Menge × 1,69, bei 0,92 €/$ (Werte der Session Y × 1,69 / 1,08 gerundet). Typische Mengen aus den echten Prompts
(Coach 8 200 ein / 1 400 aus, Chat 6 700 / 500, Ausfüllen 900 / 120; Gemini zählt ca. 1 000 Thinking-Token als Ausgabe dazu),
Methode in DECISIONS.md „Kostenschätzung“. Die App rechnet die Schätzung aus `/v1/config` (Preise, `markupBp`, `usageProfiles`); unter jeder Antwort steht der echte Preis.

### Rechnung (Session AA): ein Aufschlag, der jeden Kanal deckt
Ziel: Jeder Kauf ist **auch bei vollständig verbrauchtem Guthaben mindestens kostendeckend**, plus ein kleiner, offener Gewinn.

**Annahmen**
- USt **19 % (DE)** als Basis. Andere EU-Sätze (17–27 %) weichen ab; bei 25 % bliebe im Store-Kanal rund 3 % Gewinn, bei 27 % rund 1,6 %, also weiter kein Verlust. Beide Kanäle verkaufen zum Endpreis inkl. USt.
- **Paddle:** 5 % + 0,50 € je Transaktion, gerechnet auf den Bruttopreis (konservativ; die Preisseite nennt „5 % + 50¢ pro Checkout-Transaktion“ und sagt nichts zur Steuerbasis; für Produkte unter 10 $ bietet Paddle individuelle Preise an, noch nicht angefragt). Geprüft am 3.11.2026 auf paddle.com/pricing. Paddle führt die USt ab (Merchant of Record).
- **Stores:** Small-Business-Gebühr **15 % auf den Preis ohne USt** (Apple und Google rechnen die Provision auf den Nettopreis; die Stores führen die USt ab). Bisher stand in der Rechnung ein Brutto-Ansatz ohne USt-Abzug, das war falsch.
- **Spot-Gutschriften (je 1 €) und Test-Startguthaben sind reine Kosten** (Guthaben ohne Einnahme). Annahme: **10 % vom Netto** jedes Kaufs als Rücklage dafür. Das deckt z. B. 1 Starter-/Spot-Euro je 10 verkaufte Euro; real begrenzt `SPOT_GLOBAL_MONTHLY_BUDGET_MICRO` (50 €/Monat) die Summe zusätzlich nach oben.
- Guthaben wird vollständig zum Listenpreis × (1 + Aufschlag) verbraucht, also Anbieterkosten = Guthaben ÷ (1 + Aufschlag). Das ist der ungünstigste Fall (ungenutztes Guthaben ist Gewinn). Wechselkurs 0,92 €/$; Schwankungen und Rückbuchungen fängt der Gewinn auf.
- **Zielgewinn 7 % vom Netto** (Spanne 5–10 %). Bescheiden, weil die App keine Werbung und kein Abo hat; groß genug, um Betrieb (Cloudflare, klein), Rückbuchungen und Kursschwankungen abzufedern. Untergrenze 5 % ist im Test festgeschrieben.

**Formel:** Netto = Preis − USt − Gebühr. Es muss gelten: Preis ÷ (1 + Aufschlag) = Netto × (1 − 10 % Spot − 7 % Gewinn). Daraus Aufschlag = Preis ÷ (Netto × 0,83) − 1.

| Kanal / Paket | Netto-Anteil vom Preis | nötiger Aufschlag (7 % Gewinn) |
|---|---|---|
| Web 5 € | 69,0 % (3,45 €) | 74,5 % |
| Web 10 € | 74,0 % (7,40 €) | 62,7 % |
| Web 20 € | 76,5 % (15,31 €) | 57,4 % |
| Web 50 € | 78,0 % (39,02 €) | 54,4 % |
| **Store (alle Pakete, proportionale Gebühr)** | **71,4 %** | **68,7 %** |

Der **Store ist der ungünstigste angebotene Kanal** (15 % Gebühr zusätzlich zur USt, keine Fixgebühr). Ergebnis: **`MARKUP_BP = 6900` (69 %)**, auf 68,7 % aufgerundet.

**Das 5-€-Web-Paket treibt den Aufschlag nach oben** (die feste 0,50 € sind dort 10 % des Preises): mit ihm wären 74,5 % nötig, ohne es 68,7 %, also **5,8 Punkte weniger für alle Nutzer**. Deshalb **Web-Pakete 10 / 20 / 50 €** (statt 5 / 10 / 20 €). Im Store bleiben `overfit.fuel.5` und `.10`: dort gibt es keine Fixgebühr, ein 5-€-Paket ändert den nötigen Aufschlag nicht. IDs bleiben stabil, nichts läuft aus.

**Ergebnis je Kauf bei Aufschlag 69 %, Guthaben voll verbraucht:**

| Kanal | Preis | USt | Gebühr | Netto | Anbieterkosten (Preis ÷ 1,69) | Spot-Rücklage (10 % Netto) | Gewinn | % vom Netto | % vom Preis |
|---|---|---|---|---|---|---|---|---|---|
| Web | 10 € | 1,60 € | 1,00 € | 7,40 € | 5,92 € | 0,74 € | **0,75 €** | 10,1 % | 7,5 % |
| Web | 20 € | 3,19 € | 1,50 € | 15,31 € | 11,83 € | 1,53 € | **1,94 €** | 12,7 % | 9,7 % |
| Web | 50 € | 7,98 € | 3,00 € | 39,02 € | 29,59 € | 3,90 € | **5,53 €** | 14,2 % | 11,1 % |
| Store | 5 € | 0,80 € | 0,63 € | 3,57 € | 2,96 € | 0,36 € | **0,26 €** | 7,2 % | 5,1 % |
| Store | 10 € | 1,60 € | 1,26 € | 7,14 € | 5,92 € | 0,71 € | **0,51 €** | 7,2 % | 5,1 % |
| Store | 20 € | 3,19 € | 2,52 € | 14,29 € | 11,83 € | 1,43 € | **1,02 €** | 7,2 % | 5,1 % |

Kein Kanal macht Verlust. Der Test `backend/test/pricing.test.ts` rechnet diese Tabelle mit den Konstanten nach und schlägt fehl, wenn ein Paket oder der Aufschlag so geändert wird, dass der Gewinn unter 5 % vom Netto fällt.

### Aufschlag (offen, Transparenz)
- **69 % Aufschlag** auf den Listenpreis der Anbieter, auf jede Anfrage, für **alle** Modelle gleich. Wofür: **Umsatzsteuer** (rund 16 % des Preises), **Zahlungs- bzw. Store-Gebühr** (im Web 1,00–3,00 €, im Store 15 % vom Netto), **Gutschriften** für Startguthaben und Einladungen (Spot), **Betrieb** und ein kleiner **Gewinn** (7–10 % vom Netto).
- Das Guthaben selbst bleibt 1:1; es gibt keinen versteckten Abzug beim Kauf. Wer 10 € kauft, hat 10 € Guthaben und sieht bei jeder Antwort, was sie gekostet hat.
- Die App sagt das unter „Guthaben“ in einem kurzen Satz und verlinkt „Wie setzen sich die Preise zusammen?“ auf `https://bigfire3.github.io/overfit/preise/` (DE+EN, Beispielrechnung, ohne Werbesprache).
- **Verfall:** Startguthaben (Spot) verfällt nach 180 Tagen, gekauftes Guthaben nie.
- **Ausweichung:** Antwortet ein anderes Modell als das gewählte, zahlt der Nutzer höchstens den Preis des gewählten; ist das Ersatzmodell teurer, trägt die Marge die Differenz (sie kann dann negativ sein). Die App sagt dem Nutzer, welches Modell geantwortet hat.
- **Auswertung für den Betreiber:** `GET /v1/admin/usage` (docs/backend-api.md) zeigt je Modell Anbieterkosten, Berechnetes und Marge, ohne Inhalte.
- Preise pflegt der Betreiber in `backend/src/providers.ts`, den Aufschlag in `backend/wrangler.jsonc` (`MARKUP_BP`).

## Guthaben im Web (Session Z, Testmodus; Pakete und Rechnung Session AA)
- Kaufseite `https://bigfire3.github.io/overfit/guthaben/`, Pakete **10 €, 20 €, 50 €**. Die Preise sind **Endpreise inkl. USt**,
  das Guthaben entspricht dem Betrag: **10 € zahlen = 10 € Guthaben**, ohne Abzug; die Marge steckt allein im Aufschlag (`MARKUP_BP`).
- **Kauf ohne Login:** „Im Web aufladen“ in der App holt einen kurzlebigen, einmaligen Kauflink (15 min) zur anonymen Konto-ID und
  öffnet ihn im Browser. Zahlung über **Paddle als Merchant of Record** (übernimmt EU-USt, Beleg, Erstattungen). Ein Webhook bucht
  das Guthaben idempotent; Erstattung/Chargeback holt es zurück (nie unter 0) und markiert den Vorfall.
- **Guthaben-Codes:** ohne App auf derselben Seite kaufbar (Geschenk), einmalig in der App einlösbar („Code einlösen“).
- **Store-Regeln:** Der App-Knopf ist per `/v1/config` → `webPurchase.channels` schaltbar. Standard: nur in seitlich installierten
  APKs (GitHub), in Store-Builds aus, bis die Rechtslage geprüft ist (EU: DMA-Programme der Stores, USA: externe Links nach den
  Epic-Urteilen; Apple verlangt dann parallel den In-App-Kauf). „Code einlösen“ bleibt überall (Codes kauft man außerhalb der App).
- **Vor dem Live-Start (Fabian):** Paddle-Live-Konto mit Domain- und Kontoprüfung, Impressum, Datenschutz, AGB/Widerruf (die Seite
  zeigt Platzhalter „folgt vor dem Start“), juristische Prüfung, dann Repo-Variable `PADDLE_ENV = live` und Live-Keys. Optional bei Paddle
  anfragen, ob für Pakete unter 10 € bessere Gebühren möglich sind (dann ließe sich der Aufschlag senken).
- Backend-Vertrag: `docs/backend-api.md`, Abschnitt „Web-Kauf und Guthaben-Codes“.

## Später
1. **Pro-Abo** (optional): Duo, erweiterte Statistiken, verschlüsseltes Cloud-Backup/Sync, mehr Coach-Gedächtnis, etwas Guthaben inklusive.
2. **Programme** zum Kaufen, auch von Trainern mit Umsatzbeteiligung.
3. **Trainer-/Studio-Version.**
4. **Einmaliger Unterstützer-Kauf.**

## Offene Punkte für Fabian
- Gewerbe anmelden, Umsatzsteuer (Kleinunternehmer vs. Regelbesteuerung), Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung (digitale Inhalte), Paddle-Live-Konto, Store-Konten (Small-Business-Programme beantragen), juristische Prüfung der externen Kaufwege.
