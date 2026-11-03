# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt ist heute nur das Guthaben (mit Modellwahl) mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`); alles andere hier ist geplant.

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

## Guthaben günstiger im Web (geplant)
- Eigene Webseite mit niedrigerem Preis (keine Store-Gebühr). Aus der App wird nur dorthin verlinkt, **wo es erlaubt ist**: EU über die DMA-Programme der Stores, USA über externe Links nach den Epic-Urteilen. Die Regeln ändern sich laufend und müssen **vor dem Start juristisch geprüft** werden; pro Land per Konfiguration schaltbar.
- **Kauf ohne Login:** Die App öffnet einen kurzlebigen, signierten Kauflink mit der anonymen Konto-ID. Zahlung über einen **Merchant of Record** (z. B. Paddle oder Lemon Squeezy; übernehmen die EU-Umsatzsteuer; Stripe als Alternative, dann USt selbst). Ein Webhook bucht das Guthaben aufs Konto.
- **Guthaben-Codes:** zum Einlösen in der App, als Geschenk oder ohne App kaufbar.
- **E-Mail-Login** optional, erst später für Geräte-Sync nötig (bis dahin: Wiederherstellungscode).
- Backend-Vertrag: `docs/backend-api.md`, Abschnitt „Geplant: Web-Kauf“. Noch nicht gebaut.

## Später
1. **Pro-Abo** (optional): Duo, erweiterte Statistiken, verschlüsseltes Cloud-Backup/Sync, mehr Coach-Gedächtnis, etwas Guthaben inklusive.
2. **Programme** zum Kaufen, auch von Trainern mit Umsatzbeteiligung.
3. **Trainer-/Studio-Version.**
4. **Einmaliger Unterstützer-Kauf.**

## Offene Punkte für Fabian
- Gewerbe anmelden, Umsatzsteuer (Kleinunternehmer vs. Regelbesteuerung), Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung (digitale Inhalte), Konto beim Merchant of Record, Store-Konten (Small-Business-Programme beantragen), juristische Prüfung der externen Kaufwege.
