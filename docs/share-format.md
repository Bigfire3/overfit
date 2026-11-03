# Teilen-Format (Phase 8)

Verbindlich für App (Session B) und Web-Fallback-Seite (Session C). Implementierung: `shared/.../share/` (commonMain).

## Link

```
https://bigfire3.github.io/overfit/t#<payload>
overfit://t#<payload>            (App-Schema, gleiche Nutzlast)
```

`<payload>` = JSON (UTF-8) → **Raw Deflate** (RFC 1951, *kein* zlib-/gzip-Header; in JS
`new CompressionStream('deflate-raw')` bzw. pako `deflateRaw`/`inflateRaw`) → **Base64url ohne Padding**
(`A–Z a–z 0–9 - _`). Das Fragment erreicht nie einen Server. Beim Lesen werden zusätzlich Padding (`=`),
normales Base64 (`+` `/`) und Leerzeichen/Zeilenumbrüche toleriert.

Ist der Inhalt zu groß für einen QR-Code (> ca. 1.000 Zeichen im Link), verschickt die App stattdessen eine
Datei `*.overfit` (MIME `application/x-overfit`) mit demselben **JSON (unkomprimiert, UTF-8)**.

## JSON

```json
{
  "v": 1,
  "kind": "template",
  "name": "Push",
  "note": "optional",
  "exercises": [
    { "id": "wg:bench-press", "restSec": 150, "note": "optional",
      "sets": [ { "kg": 60, "reps": 8 }, { "kg": 60, "reps": 8 } ] },
    { "name": "Klimmzug", "sets": [ { "reps": 8 } ] },
    { "ref": "c1", "sets": [ { "sec": 45 } ] }
  ],
  "custom": [ { "ref": "c1", "name": "Handstand", "trackingType": "TIME" } ]
}
```

| Feld | Bedeutung |
|---|---|
| `v` | Format-Version, aktuell `1`. Höhere Version → „App aktualisieren“, nicht importieren. Unbekannte Felder werden ignoriert. |
| `kind` | `"template"` (Vorlage) oder `"workout"` (abgeschlossenes Training, seit Phase 3: gleiche Felder; `sets` sind entweder die geloggten Ist-Werte – nur erledigte Sätze – oder die geplanten Werte; nie Notizen, Gefühl, Fotos). Beide werden als Vorlage importiert. Unbekannte `kind` → nicht importierbar. |
| `name` | Name der Vorlage (Pflicht, max. 80 Zeichen). |
| `note` | Optionale Notiz zur Vorlage. |
| `exercises[]` | Übungen in Reihenfolge. Je Eintrag **genau eine** Referenz: `id` (Katalog-ID, z. B. `wg:bench-press`; alte Links können noch `fedb:Barbell_Curl` enthalten, siehe unten), `name` (freier Name DE/EN, wird über die unscharfe Suche aufgelöst; nicht auflösbar → eigene Übung) oder `ref` (Verweis auf `custom[]`). Reihenfolge der Auswertung: `ref` > `id` > `name`. |
| `exercises[].sets[]` | Zielwerte je Satz: `kg` (Gewicht in **kg**, negativ = Unterstützung), `reps`, `sec` (Sekunden). Alle optional. Nur die zur Erfassungsart passenden Felder sind sinnvoll (`WEIGHT_REPS`: kg+reps, `REPS`: reps, `TIME`: sec, `WEIGHT_TIME`: kg+sec). |
| `exercises[].restSec` | Pause in Sekunden (optional). |
| `exercises[].note` | Notiz zur Übung (optional). |
| `custom[]` | Eigene Übungen des Absenders: `ref` (lokaler Schlüssel), `name`, `trackingType` (`WEIGHT_REPS` \| `REPS` \| `TIME` \| `WEIGHT_TIME`), optional `primary`/`secondary` (Listen von Muskel-Schlüsseln), `equipment` (Schlüssel wie in der App), `restSec` (Standard-Pause der Übung), `note` (Ausführungsnotiz, max. 500 Zeichen). `muscle` (= erster Wert von `primary`) wird weiter geschrieben, damit ältere App-Versionen den Hauptmuskel lesen; fehlt `primary`, gilt `muscle`. Die Felder `primary`, `secondary`, `restSec`, `note` kamen im November 2026 dazu (abwärtskompatibel, gleiche Version `v: 1`). |

Grenzen: max. 50 Übungen, 30 Sätze je Übung, 200 KB entpackt. Kaputte Eingaben ergeben einen Fehler, nie einen Absturz.

## Auflösung beim Import

1. `ref` → Eintrag in `custom[]`: gibt es lokal schon eine eigene Übung mit gleichem Namen (ohne Groß-/Kleinschreibung
   und Leerzeichen am Rand) oder eine Katalogübung mit genau diesem Namen, wird sie genutzt (keine Dublette); sonst wird
   eine vollständige eigene Übung mit allen übertragenen Feldern angelegt.
2. `id` → Katalog-ID, wenn vorhanden; sonst Fallback auf `name` (falls angegeben); sonst wird aus der ID ein Name gebildet
   (`fedb:Barbell_Curl` → „Barbell Curl“), der nur bei exakt gleichem Namen auf eine vorhandene Übung passt, sonst
   entsteht eine eigene Übung mit diesem Namen.
3. `name` → bester Treffer der Übungssuche (DE/EN, Tippfehler tolerant); kein Treffer → neue eigene Übung (Erfassungsart
   aus den Satzfeldern abgeleitet).

### free-exercise-db-IDs (`fedb:`)

free-exercise-db wurde am 3.11.2026 aus dem Katalog entfernt. Neue Links enthalten keine `fedb:`-IDs mehr.
Eingehende alte Links mit `fedb:`-IDs funktionieren weiter:
- Hat der Empfänger die Übung schon benutzt, existiert sie bei ihm als eigene Übung mit derselben `fedb:`-ID (Migration v5)
  und wird direkt gefunden.
- Sonst greift der Fallback über `name` (falls im Eintrag vorhanden) bzw. über den aus der ID gebildeten Namen (Schritt 2).

## Für die Web-Fallback-Seite

Die Seite muss nur lesen (Fragment → Base64url → inflate-raw → JSON). Anzeige: Name, Übungen (`id` ohne Katalog als lesbarer
Name aus der ID oder `name`/`custom[].name`), Sätze. Zusätzlich Button „In Overfit öffnen“ auf `overfit://t#<payload>`.
