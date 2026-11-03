# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt ist heute nur Fuel (mit Preisstufen) mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`); alles andere hier ist geplant.

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

## Fuel günstiger im Web (geplant)
- Eigene Webseite mit niedrigerem Preis (keine Store-Gebühr). Aus der App wird nur dorthin verlinkt, **wo es erlaubt ist**: EU über die DMA-Programme der Stores, USA über externe Links nach den Epic-Urteilen. Die Regeln ändern sich laufend und müssen **vor dem Start juristisch geprüft** werden; pro Land per Konfiguration schaltbar.
- **Kauf ohne Login:** Die App öffnet einen kurzlebigen, signierten Kauflink mit der anonymen Konto-ID. Zahlung über einen **Merchant of Record** (z. B. Paddle oder Lemon Squeezy; übernehmen die EU-Umsatzsteuer; Stripe als Alternative, dann USt selbst). Ein Webhook bucht das Fuel aufs Konto.
- **Fuel-Codes:** zum Einlösen in der App, als Geschenk oder ohne App kaufbar.
- **E-Mail-Login** optional, erst später für Geräte-Sync nötig (bis dahin: Wiederherstellungscode).
- Backend-Vertrag: `docs/backend-api.md`, Abschnitt „Geplant: Web-Kauf“. Noch nicht gebaut.

## Später
1. **Pro-Abo** (optional): Duo, erweiterte Statistiken, verschlüsseltes Cloud-Backup/Sync, mehr Coach-Gedächtnis, etwas Fuel inklusive.
2. **Programme** zum Kaufen, auch von Trainern mit Umsatzbeteiligung.
3. **Trainer-/Studio-Version.**
4. **Einmaliger Unterstützer-Kauf.**

## Offene Punkte für Fabian
- Gewerbe anmelden, Umsatzsteuer (Kleinunternehmer vs. Regelbesteuerung), Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung (digitale Inhalte), Konto beim Merchant of Record, Store-Konten (Small-Business-Programme beantragen), juristische Prüfung der externen Kaufwege.
