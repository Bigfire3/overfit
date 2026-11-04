#!/usr/bin/env python3
"""Baut den Übungskatalog der App aus Workout Guide (fest gepinnter Commit).

Quelle (Commit-SHA gepinnt, damit der Import reproduzierbar ist):
  * Workout Guide (Bryl Lim, Grafiken auf Basis von Everkinetic, CC BY-SA 4.0); alle 3 Frames als SVG werden offline
    mitgebündelt (Frame 1 = Standbild in Listen, Frames 2/3 nur für die Animation in der großen Detailansicht). free-exercise-db wurde am 3.11.2026 entfernt (Fotos passten nicht zum Design), eigene
    Übungen legt man in der App an (auf Wunsch per KI ausgefüllt).

Ergebnis:
  shared/src/commonMain/composeResources/files/catalog/exercises.json
  shared/src/commonMain/composeResources/files/catalog/wg/<slug>.svg      (Frame 1)
  shared/src/commonMain/composeResources/files/catalog/wg/<slug>-2.svg    (Frame 2)
  shared/src/commonMain/composeResources/files/catalog/wg/<slug>-3.svg    (Frame 3)
  (Zahlen in den Pfaden auf PRECISION Nachkommastellen gerundet, sonst unverändert – siehe DECISIONS.md)
  shared/src/commonMain/composeResources/files/catalog/LICENSE-ASSETS-workout-guide.txt
(Compose Resources: gemeinsam für Android und iOS.)

Aufruf (aus dem Repo-Root):  python3 tools/catalog/import_catalog.py
Nur Python-Standardbibliothek. Downloads werden unter tools/catalog/.cache zwischengespeichert.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import re
import urllib.request
from pathlib import Path

WG_SHA = "aac599224bb9780305239607ef98540b7e0ce389"
WG_RAW = f"https://raw.githubusercontent.com/bryllim/workout-guide/{WG_SHA}/packages/workout-guide"

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "shared/src/commonMain/composeResources/files/catalog"

# --- Vereinheitlichte Schlüssel (deutsche Bezeichnungen stehen in der App) -------------------

MUSCLES = {
    # Workout Guide
    "chest": "chest", "shoulders": "shoulders", "rear delts": "shoulders", "biceps": "biceps",
    "triceps": "triceps", "forearms": "forearms", "grip": "forearms", "lats": "lats",
    "upper back": "upper_back", "back": "upper_back", "lower back": "lower_back", "core": "core",
    "glutes": "glutes", "quads": "quads", "hamstrings": "hamstrings", "calves": "calves",
    "adductors": "adductors", "groin": "adductors", "hips": "abductors", "legs": "legs",
    "posterior chain": "legs", "mobility": "mobility", "cardio": "cardio",
}

EQUIPMENT = {
    # Workout Guide
    "bodyweight": "bodyweight", "wall": "bodyweight", "doorway": "bodyweight", "chair": "bench",
    "towel": "other", "pull-up bar": "pullup_bar", "barbell": "barbell", "dumbbell": "dumbbell",
    "kettlebell": "kettlebell", "cable": "cable", "machine": "machine", "resistance band": "band",
    "plate": "plate", "box": "bench", "bench": "bench", "stability ball": "ball", "cardio": "cardio",
}

WG_TRACKING = {
    "weight_reps": "WEIGHT_REPS",
    "assisted_bodyweight": "WEIGHT_REPS",  # negatives Gewicht = Unterstützung
    "bodyweight_reps": "REPS",
    "duration": "TIME",
    "distance_duration": "TIME",  # Distanz wird nicht erfasst (nur kg/Zeit laut SPEC)
}

FRAMES = (1, 2, 3)
# Nachkommastellen in den Pfaddaten (Grafik 512x512; Rundung <= 0,005 px, nicht sichtbar). Bis 0.1.257 wurde hier per
# Regex auf 1 Stelle gerundet und dabei kompakte Zahlenfolgen wie `8.002.392` zu `8.4` verschmolzen (kaputte Bilder).
# Deshalb jetzt ein echter Pfad-Parser (Befehle, Parameterzahl, Arc-Flags, Exponenten) mit Rückprüfung.
PRECISION = 2

_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}
_NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_SEP = re.compile(r"[\s,]*")


def parse_path(d: str) -> list[tuple[str, list[float]]]:
    """Zerlegt SVG-Pfaddaten streng nach Grammatik (SVG 1.1, Kap. 8.3.9) in (Befehl, Zahlen)."""
    out: list[tuple[str, list[float]]] = []
    i, n, cmd = 0, len(d), None
    while True:
        i = _SEP.match(d, i).end()
        if i >= n:
            return out
        if d[i].isalpha():
            cmd = d[i]
            if cmd.upper() not in _ARGS:
                raise ValueError(f"Unbekannter Befehl {cmd!r} an Stelle {i}")
            i += 1
            if cmd.upper() == "Z":
                out.append((cmd, []))
                continue
        elif cmd is None or cmd.upper() == "Z":
            raise ValueError(f"Zahl ohne Befehl an Stelle {i}")
        vals: list[float] = []
        for k in range(_ARGS[cmd.upper()]):
            i = _SEP.match(d, i).end()
            if cmd.upper() == "A" and k in (3, 4):
                if i >= n or d[i] not in "01":
                    raise ValueError(f"Arc-Flag erwartet an Stelle {i}")
                vals.append(float(d[i]))
                i += 1
                continue
            m = _NUMBER.match(d, i)
            if not m:
                raise ValueError(f"Zahl erwartet an Stelle {i}: {d[i:i + 20]!r}")
            vals.append(float(m.group(0)))
            i = m.end()
        out.append((cmd, vals))
        # Wiederholte Parameter nach M/m gelten als L/l.
        if cmd in "Mm":
            cmd = "L" if cmd == "M" else "l"


def _fmt(v: float) -> str:
    out = f"{round(v, PRECISION):.{PRECISION}f}".rstrip("0").rstrip(".")
    if out in ("", "-0", "-"):
        return "0"
    if out.startswith("0."):
        return out[1:]
    if out.startswith("-0."):
        return "-" + out[2:]
    return out


def write_path(segs: list[tuple[str, list[float]]]) -> str:
    """Kompakte, eindeutige Ausgabe: Trenner nur, wo die nächste Zahl sonst mit der vorigen verschmelzen würde."""
    parts: list[str] = []
    prev: str | None = None  # letzte ausgegebene Zahl, None direkt nach einem Befehl
    implicit: str | None = None  # Befehl, der ohne Buchstaben weitergilt (M → L, m → l, sonst derselbe)
    for cmd, vals in segs:
        if cmd != implicit or not vals:
            parts.append(cmd)
            prev = None
        implicit = {"M": "L", "m": "l"}.get(cmd, cmd)
        for k, v in enumerate(vals):
            tok = str(int(v)) if cmd.upper() == "A" and k in (3, 4) else _fmt(v)
            if prev is not None and (tok[0].isdigit() or (tok[0] == "." and "." not in prev)):
                parts.append(" ")
            parts.append(tok)
            prev = tok
    return "".join(parts)


def shrink(svg: bytes) -> bytes:
    """Rundet die Zahlen in `d="…"` auf PRECISION Stellen; prüft, dass die Ausgabe wieder dieselben Befehle ergibt."""
    def fix_d(m: re.Match) -> str:
        segs = parse_path(m.group(1))
        d = write_path(segs)
        back = parse_path(d)
        assert [c for c, _ in back] == [c for c, _ in segs], "Befehlsfolge nach Rundung verändert"
        for (_, a), (_, b) in zip(segs, back):
            assert len(a) == len(b) and all(abs(x - y) <= 0.5 * 10 ** -PRECISION + 1e-9 for x, y in zip(a, b)), \
                "Zahlen nach Rundung verändert"
        return 'd="' + d + '"'
    return re.sub(r'\bd="([^"]*)"', fix_d, svg.decode("utf-8")).encode("utf-8")


def fetch(url: str, cache_name: str) -> bytes:
    path = CACHE / cache_name
    if path.exists():
        return path.read_bytes()
    with urllib.request.urlopen(url, timeout=60) as resp:
        data = resp.read()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return data


def map_muscles(raw: list[str], where: str) -> list[str]:
    out: list[str] = []
    for m in raw:
        key = MUSCLES.get(m.lower())
        if key is None:
            raise SystemExit(f"Unbekannte Muskelgruppe {m!r} in {where}")
        if key not in out:
            out.append(key)
    return out


def map_equipment(raw: str | None, where: str) -> str:
    key = EQUIPMENT.get((raw or "none").lower())
    if key is None:
        raise SystemExit(f"Unbekanntes Equipment {raw!r} in {where}")
    return key


def main() -> None:
    print("Lade Workout Guide Manifest …")
    wg = json.loads(fetch(f"{WG_RAW}/manifest.json", f"wg-{WG_SHA}/manifest.json"))

    def frames(e: dict) -> list[tuple[str, bytes]]:
        out = []
        for idx in FRAMES:
            path = next((f["path"] for f in e["frames"] if f["index"] == idx), None)
            if path is None or not path.endswith(".svg"):
                raise SystemExit(f"Frame {idx} von {e['slug']} fehlt oder ist kein SVG: {path}")
            name = f"{e['slug']}.svg" if idx == 1 else f"{e['slug']}-{idx}.svg"
            out.append((name, shrink(fetch(f"{WG_RAW}/{path}", f"wg-{WG_SHA}/{path}"))))
        return out

    print(f"Lade {len(wg)} x {len(FRAMES)} SVG-Frames …")
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        svgs = dict(f for fl in pool.map(frames, wg) for f in fl)
    license_text = fetch(f"{WG_RAW}/LICENSE-ASSETS", f"wg-{WG_SHA}/LICENSE-ASSETS")

    exercises: list[dict] = []
    for e in sorted(wg, key=lambda x: x["name"].lower()):
        where = f"Workout Guide {e['slug']}"
        item = {
            "id": f"wg:{e['slug']}",
            "name": e["name"],
            "source": "WORKOUT_GUIDE",
            "image": f"catalog/wg/{e['slug']}.svg",
            "primary": map_muscles([e["primaryMuscle"]], where),
            "secondary": map_muscles(e.get("secondaryMuscles", []), where),
            "equipment": map_equipment(e["equipment"], where),
            "tracking": WG_TRACKING[e["exerciseType"]],
        }
        exercises.append(item)

    ids = [x["id"] for x in exercises]
    assert len(ids) == len(set(ids)), "Doppelte IDs"

    payload = json.dumps(exercises, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    version = hashlib.sha256(payload.encode()).hexdigest()[:12]
    doc = {
        "version": version,
        "sources": {
            "workoutGuide": {"repo": "github.com/bryllim/workout-guide", "commit": WG_SHA, "license": "CC BY-SA 4.0",
                             "artwork": "Everkinetic (CC BY-SA 4.0)"},
        },
        "exercises": exercises,
    }

    svg_dir = OUT / "wg"
    if svg_dir.exists():
        for old in svg_dir.glob("*.svg"):
            old.unlink()
    svg_dir.mkdir(parents=True, exist_ok=True)
    for name, data in svgs.items():
        (svg_dir / name).write_bytes(data)
    (OUT / "LICENSE-ASSETS-workout-guide.txt").write_bytes(license_text)
    (OUT / "exercises.json").write_text(
        json.dumps(doc, ensure_ascii=False, indent=0, sort_keys=False) + "\n", encoding="utf-8"
    )

    print(f"Fertig: {len(exercises)} Übungen (Workout Guide), Version {version}")


if __name__ == "__main__":
    main()
