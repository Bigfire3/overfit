# Monetarisierung

Marke: **overfit – personal training for everyone** (siehe DECISIONS.md). Stand: Plan, mit Fabian abgestimmt. Umgesetzt ist heute das Guthaben (mit Modellwahl, Aufschlag 28 %, im Web 1:1, im Store mit offen ausgewiesenem Aufpreis) mit Store-Kauf im Backend-Vertrag (`/v1/purchases/verify`); dazu der Web-Kauf mit Guthaben-Codes **im Testmodus** (Stripe, Session AB; vorher Paddle-Sandbox, Session Z); alles andere hier ist geplant.

## Grundsätze
- **Grundfunktionen kostenlos, keine Werbung.** Training loggen, Katalog, Workouts, Pausentimer, Auswertung, Diary, Teilen, eigener API-Key bleiben gratis.
- Bezahlt wird nur, was Kosten verursacht (KI über das Guthaben) oder späterer Mehrwert (Pro).
- Texte versprechen „kein Abo nötig“, nie „nie ein Abo“.

## Guthaben (heute)
- Coach-Guthaben in Euro („Guthaben“, EN „Credit“; früher „Fuel“). Kauf **im Web** über Stripe (Fabian verkauft direkt, als Kleinunternehmer nach § 19 UStG ohne USt) oder **in der App** über Google Play Billing bzw. Apple In-App-Kauf. **Preise je Kanal** (Fabians Entscheidung, Session AB): **Web 1:1** (10 € zahlen = 10 € Guthaben), **Store mit offen ausgewiesenem Aufpreis** („5 € Guthaben für 6,99 €“), weil Apple/Google als Verkäufer USt abführen und 15 % Gebühr nehmen. Verbraucht wird überall gleich: Listenpreis × (1 + `MARKUP_BP`). Die Store-Produkt-IDs `overfit.fuel.5` / `.10` bleiben (bei den Stores registriert, nicht sichtbar).
- Apple verlangt, dass es das Guthaben **parallel als In-App-Kauf** gibt, sobald die App auf externe Kaufwege verweist.

### Modelle und Preise (seit 7.11.2026 nur Claude; Session Y: echte Namen; Session AB: Aufschlag 28 %)
Es gibt keine Marketing-Namen wie „Standard“, „Pro“ oder „Lite“. Die App zeigt die Modelle, die wirklich antworten, mit Anbieter, und was sie
kosten. Abgerechnet wird je Anfrage nach Token zum Listenpreis des Modells plus **28 % Aufschlag** (`MARKUP_BP = 2800`, ein einziger Wert für alle Modelle und Kanäle).
Fabians Wunsch (7.11.): möglichst alle Claude-Modelle, ohne eigene API-Schlüssel; kurze, günstige Anfragen mit Haiku. Deshalb laufen alle
Guthaben-Anfragen über **einen** Anthropic-Schlüssel des Betreibers. Der Nutzer wählt das Modell (Coach-Einstellungen → „Modell für deinen Coach“,
Voreinstellung Claude Sonnet 5.5); „Mit KI ausfüllen“ und geschenkte Auswertungen nutzen immer Claude Haiku 5.5.

| Modell (Anbieter) | Listenpreis je 1 Mio. Token (ein / aus / Cache lesen) | Coach-Auswertung* | Chat-Nachricht* | „Mit KI ausfüllen“* |
|---|---|---|---|---|
| Claude Haiku 5.5 (Anthropic) | 0,10 $ / 0,50 $ / 0,01 $ | ≈ 0,18 ct | ≈ 0,11 ct | ≈ 0,02 ct (immer dieses Modell) |
| Claude Sonnet 5.5 (Anthropic), Voreinstellung | 2 $ / 10 $ / 0,20 $ | ≈ 3,6 ct | ≈ 2,2 ct | – |
| Claude Opus 5.5 (Anthropic) | 4 $ / 20 $ / 0,20 $ | ≈ 7,2 ct | ≈ 4,3 ct | – |

Quelle: Preisliste von Anthropic (Stand 7.11.2026); Cache schreiben = 1,25 × Eingabe. Haiku-Preis gilt bis 100 000 Token Eingabe (Overfit bleibt weit darunter).
Bis 7.11.2026: Gemini 3.6 Flash (Voreinstellung), Claude Sonnet 5.5 und Llama über Cloudflare Workers AI; alte Buchungen behalten ihren Preis.

\* berechnet, nicht geraten: Listenpreis × typische Token-Menge × 1,28, bei 0,92 €/$ (z. B. Sonnet-Auswertung 27 968 µ€ × 1,28 = 35 799 µ€). Typische Mengen aus den echten Prompts
(Coach 8 200 ein / 1 400 aus, Chat 6 700 / 500, Ausfüllen 900 / 120). Claude denkt bei Bedarf (adaptives Thinking, zählt als Ausgabe); bei
`effort: medium` und kurzen Anfragen gemessen 0 Thinking-Token, bei langen Auswertungen kann die Ausgabe größer sein.
Methode in DECISIONS.md „Kostenschätzung“. Die App rechnet die Schätzung aus `/v1/config` (Preise, `markupBp`, `usageProfiles`); unter jeder Antwort steht der echte Preis,
und Profil → Guthaben → **Abrechnung** listet jede einzelne Anfrage (Zeit, Zweck, Modell, Token, Betrag, Status, Ausweichen).

**Ausweichen:** Antwortet das gewählte Modell nicht (Überlastung, Ausfall, Zeitüberschreitung), beantwortet ein anderes Claude-Modell die
Anfrage (Haiku ↔ Sonnet, Opus → Sonnet). Der fehlgeschlagene Versuch kostet nichts. Berechnet wird für die tatsächlich verbrauchten Token
der Preis des antwortenden Modells, höchstens aber der des gewählten (antwortet Sonnet statt Haiku, zahlt der Nutzer den Haiku-Preis).

### Rechnung (Session AB): Web 1:1, Store mit Aufpreis, ein Aufschlag
Ziel: Jeder Kauf ist **auch bei vollständig verbrauchtem Guthaben mindestens kostendeckend**, plus ein kleiner, offener Gewinn.
Bis Session AA trug ein einziger Aufschlag (69 %) auch den Store, den teuersten Kanal. Jetzt trägt der Store seine Kosten selbst
(offener Aufpreis), und der Aufschlag richtet sich nach dem Web.

**Annahmen** (wie AA, plus Stripe und Kleinunternehmer)
- **Web, Kleinunternehmer (§ 19 UStG):** keine USt. Preis = Netto vor Gebühr.
- **Stripe** (stripe.com/de/pricing, geprüft am 3.11.2026): EWR-Standardkarten 1,5 % + 0,25 €, EWR-Premiumkarten 2,8 % + 0,25 €,
  UK-Karten 2,5 % + 0,25 €, Nicht-EWR-Karten 3,15 % + 0,25 €, Währungsumrechnung + 2 % (fällt nur an, wenn Stripe umrechnet; wir
  verkaufen und rechnen in EUR ab), Klarna ab 2,99 % + 0,35 €, SEPA-Lastschrift 0,35 €, Chargeback 20 €. **Rechengrundlage: konservative
  Mischung 2,5 % + 0,25 €** (realistisch wären ~2 %: überwiegend EWR-Karten, ein Teil Premium/Nicht-EWR). Zusätzlich muss **jede einzelne
  Zahlart** ohne Verlust bleiben (geprüft: Nicht-EWR-Karte mit Umrechnung 5,15 % + 0,25 €, Klarna 2,99 % + 0,35 €).
- **Stores:** Apple und Google sind Verkäufer (Kommissionär), führen die USt des Käuferlands ab und nehmen **15 % vom Preis ohne USt**
  (Small-Business-Programme). Fabians Kleinunternehmerstatus ändert daran nichts. Store-Preise sind in allen Euro-Ländern gleich, die
  USt nicht: gerechnet mit **19 % (DE)** und **27 % (höchster EU-Satz)**.
- **Geschenktes (Startguthaben, Einladungsbonus): 10 % vom Netto** als Rücklage (der Einladungsbonus kostet seit Session AD weit weniger, siehe unten). **Zielgewinn 7 % vom Netto**, Untergrenze **5 %** (im Test festgeschrieben).
- Guthaben wird vollständig zum Listenpreis × (1 + Aufschlag) verbraucht (ungünstigster Fall), Wechselkurs 0,92 €/$.

**Formel:** Netto = Preis − USt − Gebühr. Gewinn = Netto − Guthaben ÷ (1 + Aufschlag) − 10 % × Netto.
Nötiger Aufschlag für Zielanteil g: Aufschlag = Guthaben ÷ (Netto × (0,9 − g)) − 1.

**Web (Guthaben = Preis):**

| Paket | Gebühr (2,5 % + 0,25 €) | Netto | nötig für 7 % | nötig für 5 % |
|---|---|---|---|---|
| 5 € | 0,38 € | 4,63 € | 30,3 % | 27,2 % |
| 10 € | 0,50 € | 9,50 € | 26,8 % | 23,8 % |
| 20 € | 0,75 € | 19,25 € | 25,2 % | 22,2 % |
| 50 € | 1,50 € | 48,50 € | 24,2 % | 21,3 % |

**Ergebnis: `MARKUP_BP = 2800` (28 %)**, der kleinste ganze Prozentwert, mit dem **jedes** Web-Paket mindestens 5 % behält (5 €: 27,2 % →
28 %). Ab 10 € liegt der Gewinn über dem Ziel von 7 %.

**5 € im Web: ja.** Ohne das 5-€-Paket reichten 27 % (10 € mit 7 % Ziel), mit ihm 28 %: **ein Prozentpunkt für alle**. Bei Paddle waren es
5,8 Punkte (0,50 € Fixgebühr); bei Stripe ist die Fixgebühr halb so hoch und es gibt keine USt. Ein niedriger Einstieg (5 €) ist für eine App
ohne Abo den einen Punkt wert. Das 5-€-Paket erreicht 5,5 % statt 7 %, ist aber nie im Verlust (auch nicht mit der teuersten Zahlart).

**Store (Aufpreis offen, Guthaben fest):** niedrigste gängige Preisstufe (x,99 €), die bei 27 % USt noch 5 % Gewinn lässt.

| Produkt | Guthaben | Store-Preis | Aufpreis | Gewinn bei 19 % USt | bei 27 % USt | eine Stufe tiefer (x,49 €) bei 27 % |
|---|---|---|---|---|---|---|
| `overfit.fuel.5` | 5 € | **6,99 €** | 40 % | 0,59 € (11,8 %) | 0,30 € (6,5 %) | 6,49 €: 0,1 % |
| `overfit.fuel.10` | 10 € | **13,99 €** | 40 % | 1,18 € (11,8 %) | 0,61 € (6,6 %) | 13,49 €: 3,5 % |

Die 6,49/12,99/24,99 aus der Aufgabe reichen bei 28 % Aufschlag nicht (bei 27 % USt ≈ 0 % Gewinn); erst bei rund 35 % Aufschlag würden sie
passen, und den zahlten dann alle, auch im Web. Ein 20-€-Store-Paket ist nicht nötig (es wäre 27,99 €).

**Ergebnis je Kauf bei Aufschlag 28 %, Guthaben voll verbraucht:**

| Kanal | Preis | Guthaben | USt | Gebühr | Netto | Anbieterkosten (Guthaben ÷ 1,28) | Rücklage Einladungsbonus | Gewinn | % vom Netto |
|---|---|---|---|---|---|---|---|---|---|
| Web | 5 € | 5 € | – | 0,38 € | 4,63 € | 3,91 € | 0,46 € | **0,26 €** | 5,5 % |
| Web | 10 € | 10 € | – | 0,50 € | 9,50 € | 7,81 € | 0,95 € | **0,74 €** | 7,8 % |
| Web | 20 € | 20 € | – | 0,75 € | 19,25 € | 15,63 € | 1,93 € | **1,70 €** | 8,8 % |
| Web | 50 € | 50 € | – | 1,50 € | 48,50 € | 39,06 € | 4,85 € | **4,59 €** | 9,5 % |
| Store (DE) | 6,99 € | 5 € | 1,12 € | 0,88 € | 4,99 € | 3,91 € | 0,50 € | **0,59 €** | 11,8 % |
| Store (DE) | 13,99 € | 10 € | 2,23 € | 1,76 € | 9,99 € | 7,81 € | 1,00 € | **1,18 €** | 11,8 % |
| Store (27 %) | 6,99 € | 5 € | 1,49 € | 0,83 € | 4,68 € | 3,91 € | 0,47 € | **0,30 €** | 6,5 % |

Teuerste einzelne Zahlart im Web (Nicht-EWR-Karte + Umrechnung): 5 € → 3,0 %, 10 € → 5,4 %, 50 € → 7,2 %, kein Verlust. Ein Chargeback
(20 € Gebühr) frisst den Gewinn vieler Käufe; das fängt nur die Summe auf, deshalb bleibt die Rücklage für Startguthaben und Einladungsbonus konservativ.

Der Test `backend/test/pricing.test.ts` rechnet das mit den Konstanten nach: jedes Web- und Store-Paket ≥ 5 % (Store bei 19 % und 27 % USt),
keine Zahlart im Verlust, Store-Preise auf x,99 € und jeweils die niedrigste Stufe, die reicht, und der Aufschlag ist der kleinste ganze
Prozentwert, der alle Web-Pakete trägt.

### Aufschlag (offen, Transparenz)
- **28 % Aufschlag** auf den Listenpreis der Anbieter, auf jede Anfrage, für **alle** Modelle und Kanäle gleich. Wofür: **Zahlungsgebühr**
  (im Web 0,38–1,50 € je Kauf), **Geschenktes** (Startguthaben, Einladungsbonus als Coach-Auswertungen), **Betrieb** und ein kleiner **Gewinn**
  (5–10 % vom Netto). USt fällt im Web nicht an (Kleinunternehmer); im Store tragen USt und Store-Gebühr der offene Aufpreis.
- Im Web bleibt das Guthaben 1:1; im Store steht der Aufpreis bei jedem Paket („5 € Guthaben · 6,99 € (inkl. Store-Gebühr)“).
- Die App sagt das unter „Guthaben“ und verlinkt „Wie setzen sich die Preise zusammen?“ auf `https://bigfire3.github.io/overfit/preise/`
  (DE+EN, je ein Beispiel Web und Store, ohne Werbesprache).
- **Verfall:** Geschenktes (Startguthaben, Admin-Gutschrift, Einladungsbonus) verfällt nach 180 Tagen (Test-Gutschriften nach 60), gekauftes Guthaben nie.
- **Gekauft vs. geschenkt:** Der Saldo ist getrennt; Geschenktes wird zuerst verbraucht. Die App zeigt Guthaben immer als „X € Guthaben“,
  echtes Geld nur als Preis („Preis: 10,00 €“ bzw. „6,99 € (inkl. Store-Gebühr)“). Jede Buchung ist als bezahlt, geschenkt, verbraucht,
  erstattet oder verfallen markiert.
- **Ausweichung:** Antwortet ein anderes Modell als das gewählte, zahlt der Nutzer höchstens den Preis des gewählten; ist das Ersatzmodell teurer, trägt die Marge die Differenz (sie kann dann negativ sein). Die App sagt dem Nutzer, welches Modell geantwortet hat.
- **Auswertung für den Betreiber:** `GET /v1/admin/usage` (docs/backend-api.md, Tabelle mit `scripts/usage-report.mjs`) zeigt je Modell
  Anbieterkosten, Berechnetes und Marge, ohne Inhalte, den Web-Umsatz je Jahr und Land und seit Session AD einen Finanzteil in echten Euro:
  Einnahmen je Kanal (Web/Stripe, Google Play, App Store) brutto und nach Gebühren (geschätzt: Stripe 2,5 % + 0,25 €, Store 15 % auf den
  Preis ohne 19 % USt), offenes Guthaben gekauft/geschenkt, offene geschenkte Auswertungen, KI-Kosten (davon für geschenkte Auswertungen)
  und Rohgewinn = Netto-Einnahmen − KI-Kosten. Geschenktes zählt nie als Einnahme.

### Einladungsbonus: geschenkte Coach-Auswertungen (Session AD)
Fabians Entscheidung: **der Neue bekommt 5 geschenkte Auswertungen, der Einladende 5**, statt Euro-Guthaben. Auslöser wie bisher: der Freund
beim Einlösen, der Einladende nach der ersten erfolgreichen Coach-Anfrage des Freundes. Geschenkte Auswertungen sind ein eigener Zähler (kein
Euro-Betrag), gelten nur für **Coach-Auswertungen mit dem günstigsten Modell (seit 7.11.2026 Claude Haiku 5.5, vorher Gemini Flash)**, werden vor dem Guthaben
verbraucht und verfallen nach 180 Tagen. Chat und andere Modelle zahlen aus dem Guthaben. Wer eine geschenkte Auswertung mit Sonnet oder Opus
möchte, wählt dafür nicht Haiku: dann zahlt das Guthaben (bewusst: der Bonus soll Fabian kaum etwas kosten).

**Echte Kosten je Auswertung mit Haiku** (Preistabelle `backend/src/providers.ts`, typische Auswertung 8 200 Token rein, 1 400 raus, ohne Aufschlag):

| | Claude Haiku 5.5 (0,10 $ / 0,50 $ je MTok) |
|---|---|
| Auswertung ohne Foto | ≈ 0,0014 € |
| Auswertung mit 2 Fotos | ≈ 0,0017 € |
| je erfolgreiche Einladung (5 + 5 Auswertungen) | ≈ 0,014–0,017 € |
| Worst Case je Person und Monat: 10 Einladungen × 5 = 50 Auswertungen als Einladender, dazu deren 10 Freunde × 5 | ≈ 0,14–0,17 € |

Die Kostenbremsen bleiben unverändert (siehe Grenzen); mit Haiku greifen sie praktisch nie.

**Grenzen** (alle in `backend/wrangler.jsonc`):
- QR-Token 45 s und einmalig, Selbsteinladung gesperrt, je Konto und Gerät (Attestierung) eine Einlösung, 10 Einladungen pro Monat und Person.
- **Globales Monatsbudget in Auswertungen:** `INVITE_GLOBAL_MONTHLY_BUDGET_REVIEWS = 1000` (jede Einlösung zählt 5 + 5), also bis zu 100
  Einladungen im Monat, ≈ 14–31 € echte KI-Kosten.
- **Harte Kostengrenze:** `INVITE_GIFT_MONTHLY_COST_CAP_MICRO = 50 000 000` (50 €): die echten KI-Kosten aller geschenkten Auswertungen im
  Kalendermonat. Ist sie erreicht, zahlt das Guthaben, bis der Monat wechselt. Damit sind höchstens ~50 € im Monat möglich, auch wenn
  Anfragen größer ausfallen als typisch.
- **Je Anfrage:** Eine Anfrage, deren reservierte Höchstkosten zum Anbieterpreis über `INVITE_GIFT_MAX_COST_MICRO = 250 000` (0,25 €)
  liegen, zählt nicht als geschenkte Auswertung.
- **Kontoumzug:** Beim Wiederherstellen per Code verfallen geschenkte Einladungs-Auswertungen (und alte Euro-Einladungsboni) des
  aufgegebenen Kontos; gekauftes Guthaben wandert mit. Das schließt die Schleife „neu installieren → eigene Einladung einlösen → per Code
  zurück“. Eine Belohnung geht nie an das eigene Konto.
- Bestehende Euro-Einladungsboni aus dem Testbetrieb bleiben als geschenktes Guthaben gültig, bis sie verbraucht sind oder verfallen.
- Preise pflegt der Betreiber in `backend/src/providers.ts`, den Aufschlag in `backend/wrangler.jsonc` (`MARKUP_BP`), die Pakete in `backend/src/config.ts`.

## Guthaben im Web (Session Z, Testmodus; Stripe und Preise Session AB)
- Kaufseite `https://bigfire3.github.io/overfit/guthaben/`, Pakete **5 €, 10 €, 20 €, 50 €** (10 € vorgewählt). **Endpreise ohne USt**
  (Kleinunternehmer, „Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.“ auf Kaufseite, Stripe-Checkout und Bestätigung), das Guthaben
  entspricht dem Betrag: **10 € zahlen = 10 € Guthaben**.
- **Kauf ohne Login:** „Im Web aufladen“ in der App holt einen kurzlebigen, einmaligen Kauflink (15 min) zur anonymen Konto-ID und
  öffnet ihn im Browser. Bezahlt wird auf der **gehosteten Stripe-Checkout-Seite** (Karte, Apple/Google Pay, Link und die EU-Zahlarten,
  die im Stripe-Dashboard aktiv sind). Verkäufer ist Fabian; Stripe ist nur Zahlungsdienstleister (kein Merchant of Record). Ein Webhook
  bucht das Guthaben idempotent; Erstattung/Chargeback holt es zurück (nie unter 0) und markiert den Vorfall.
- **Beleg:** Stripe schickt eine Zahlungsbestätigung per E-Mail (im Testmodus nur an verifizierte Adressen). Optional `STRIPE_INVOICES=on`
  (Repo-Variable): zusätzlich eine Stripe-Rechnung (PDF) mit §-19-Fußzeile; das kostet bei Stripe extra (Invoicing).
- **Guthaben-Codes:** ohne App auf derselben Seite kaufbar (Geschenk), einmalig in der App einlösbar („Code einlösen“), ebenfalls über Stripe.
- **Store-Regeln:** Der App-Knopf ist per `/v1/config` → `webPurchase.channels` schaltbar. Standard: nur in seitlich installierten
  APKs (GitHub), in Store-Builds aus, bis die Rechtslage geprüft ist (EU: DMA-Programme der Stores, USA: externe Links nach den
  Epic-Urteilen; Apple verlangt dann parallel den In-App-Kauf). „Code einlösen“ bleibt überall (Codes kauft man außerhalb der App).
- **Vor dem Live-Start (Fabian):** Stripe-Konto aktivieren (Identität, Bankkonto, Geschäftsangaben), Live-Schlüssel, Impressum, Datenschutz,
  AGB/Widerruf (die Seite zeigt Platzhalter „folgt vor dem Start“), juristische Prüfung. Schritt für Schritt: backend/README.md → „Web-Kauf“.
- Backend-Vertrag: `docs/backend-api.md`, Abschnitt „Web-Kauf und Guthaben-Codes“.

## Kleinunternehmer und Schwellen (Hinweis, keine Steuerberatung)
Fabian ist umsatzsteuerlicher Kleinunternehmer (§ 19 UStG) und verkauft im Web direkt. Das funktioniert nur, solange bestimmte Schwellen
nicht überschritten sind. **Bitte mit dem Steuerberater prüfen**, bevor eine Schwelle erreicht wird; die Werte hier sind Stand 2026 und
ohne Gewähr.
- **Kleinunternehmergrenze (§ 19 UStG, seit 2025):** Gesamtumsatz im Vorjahr höchstens 25.000 € und im laufenden Jahr höchstens 100.000 €.
  Wird die 100.000-€-Grenze im laufenden Jahr überschritten, endet die Regelung **ab diesem Umsatz** (nicht erst im Folgejahr). Zum
  Gesamtumsatz zählen nicht nur die Web-Verkäufe, sondern auch die Erlöse aus den Stores (Auszahlungen von Apple/Google).
- **EU-Lieferschwelle für digitale Leistungen an Privatkunden in anderen EU-Ländern: 10.000 € pro Jahr** (Summe über alle anderen
  EU-Länder, Vorjahr oder laufendes Jahr). Darüber gilt die USt des Käuferlands; die deutsche Kleinunternehmerregelung hilft dann für
  diese Verkäufe nicht mehr (Anmeldung beim OSS-Verfahren, Stripe Tax o. Ä. nötig). Verkäufe in Drittländer (z. B. Schweiz, UK) haben
  eigene Regeln.
- **Werkzeug:** `GET /v1/admin/usage` liefert `webRevenue` (Web-Umsatz je Kalenderjahr, je Rechnungsland und gesamt, bezahlt minus
  erstattet), `backend/scripts/usage-report.mjs` zeigt es als Tabelle. **Warnung ab 80 %** der EU-Lieferschwelle (andere EU-Länder,
  unbekanntes Land zählt vorsichtshalber mit), der 25.000-€- und der 100.000-€-Grenze. Das Rechnungsland liefert Stripe
  (`customer_details.address.country`); gespeichert wird nur das Land, keine Namen oder Adressen. Store-Erlöse sind darin nicht enthalten.

## Später
1. **Pro-Abo** (optional): Duo, erweiterte Statistiken, verschlüsseltes Cloud-Backup/Sync, mehr Coach-Gedächtnis, etwas Guthaben inklusive.
2. **Programme** zum Kaufen, auch von Trainern mit Umsatzbeteiligung.
3. **Trainer-/Studio-Version.**
4. **Einmaliger Unterstützer-Kauf.**

## Offene Punkte für Fabian
- Gewerbe anmelden, Umsatzsteuer (Kleinunternehmer seit Session AB bestätigt; Schwellen siehe oben), Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung (digitale Inhalte), Stripe-Konto live schalten, Store-Konten (Small-Business-Programme beantragen), juristische Prüfung der externen Kaufwege.
