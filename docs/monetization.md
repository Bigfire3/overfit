# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt ist heute nur Fuel mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`); alles andere hier ist geplant.

## Grundsätze
- **Grundfunktionen kostenlos, keine Werbung.** Training loggen, Katalog, Workouts, Pausentimer, Auswertung, Diary, Teilen, eigener API-Key bleiben gratis.
- Bezahlt wird nur, was Kosten verursacht (KI über Fuel) oder späterer Mehrwert (Pro).
- Texte versprechen „kein Abo nötig“, nie „nie ein Abo“.

## Fuel (heute)
- Coach-Guthaben in Euro. Kauf **in der App** über Google Play Billing bzw. Apple In-App-Kauf. Small-Business-Gebühr der Stores: **15 %**, im Backend als `STORE_FEE_BP` eingepreist (Gutschrift = Preis × (1 − Gebühr) / (1 + Aufschlag)).
- Apple verlangt, dass es Fuel **parallel als In-App-Kauf** gibt, sobald die App auf externe Kaufwege verweist.

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
