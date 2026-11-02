#!/usr/bin/env python3
"""Baut den Übungskatalog der App aus zwei fest gepinnten Quellen.

Quellen (Commit-SHAs gepinnt, damit der Import reproduzierbar ist):
  * Workout Guide (Bryl Lim, Grafiken CC BY-SA 4.0) – Hauptquelle, Frame 1 als SVG wird offline mitgebündelt.
  * free-exercise-db (yuhonas, Unlicense) – Ergänzung, Bilder werden in der App lazy geladen.

Ergebnis:
  app/src/main/assets/catalog/exercises.json
  app/src/main/assets/catalog/wg/<slug>.svg        (unverändert aus Workout Guide)
  app/src/main/assets/catalog/LICENSE-ASSETS-workout-guide.txt

Aufruf (aus dem Repo-Root):  python3 tools/catalog/import_catalog.py
Nur Python-Standardbibliothek. Downloads werden unter tools/catalog/.cache zwischengespeichert.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import re
import sys
import urllib.request
from pathlib import Path

WG_SHA = "aac599224bb9780305239607ef98540b7e0ce389"
WG_RAW = f"https://raw.githubusercontent.com/bryllim/workout-guide/{WG_SHA}/packages/workout-guide"
FEDB_SHA = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"
FEDB_RAW = f"https://raw.githubusercontent.com/yuhonas/free-exercise-db/{FEDB_SHA}"

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "app/src/main/assets/catalog"

# --- Vereinheitlichte Schlüssel (deutsche Bezeichnungen stehen in der App) -------------------

MUSCLES = {
    # Workout Guide
    "chest": "chest", "shoulders": "shoulders", "rear delts": "shoulders", "biceps": "biceps",
    "triceps": "triceps", "forearms": "forearms", "grip": "forearms", "lats": "lats",
    "upper back": "upper_back", "back": "upper_back", "lower back": "lower_back", "core": "core",
    "glutes": "glutes", "quads": "quads", "hamstrings": "hamstrings", "calves": "calves",
    "adductors": "adductors", "groin": "adductors", "hips": "abductors", "legs": "legs",
    "posterior chain": "legs", "mobility": "mobility", "cardio": "cardio",
    # free-exercise-db
    "abdominals": "core", "quadriceps": "quads", "middle back": "upper_back", "traps": "upper_back",
    "abductors": "abductors", "neck": "neck",
}

EQUIPMENT = {
    # Workout Guide
    "bodyweight": "bodyweight", "wall": "bodyweight", "doorway": "bodyweight", "chair": "bench",
    "towel": "other", "pull-up bar": "pullup_bar", "barbell": "barbell", "dumbbell": "dumbbell",
    "kettlebell": "kettlebell", "cable": "cable", "machine": "machine", "resistance band": "band",
    "plate": "plate", "box": "bench", "bench": "bench", "stability ball": "ball", "cardio": "cardio",
    # free-exercise-db
    "body only": "bodyweight", "none": "bodyweight", "kettlebells": "kettlebell", "bands": "band",
    "e-z curl bar": "ez_bar", "medicine ball": "ball", "exercise ball": "ball", "foam roll": "other",
    "other": "other",
}

WG_TRACKING = {
    "weight_reps": "WEIGHT_REPS",
    "assisted_bodyweight": "WEIGHT_REPS",  # negatives Gewicht = Unterstützung
    "bodyweight_reps": "REPS",
    "duration": "TIME",
    "distance_duration": "TIME",  # Distanz wird nicht erfasst (nur kg/Zeit laut SPEC)
}

# Manuelle Zuordnungen free-exercise-db-ID -> Workout-Guide-Slug für Dubletten, die der
# Namensabgleich nicht erkennt. Nur eindeutige Fälle.
MANUAL_DUPLICATES = {
    "Barbell_Bench_Press_-_Medium_Grip": "bench-press",
    "Pullups": "pull-up",
    "Chin-Up": "chin-up",
    "Pushups": "push-up",
    "Barbell_Full_Squat": "squat",
    "Barbell_Deadlift": "deadlift",
    "Romanian_Deadlift": "romanian-deadlift",
    "Dips_-_Triceps_Version": "dip",
    "Plank": "plank",
    "Barbell_Hip_Thrust": "hip-thrust",
    "Standing_Military_Press": "overhead-press",
    "Bent_Over_Barbell_Row": "barbell-row",
    "Wide-Grip_Lat_Pulldown": "wide-grip-lat-pulldown",
    "Side_Lateral_Raise": "lateral-raise",
    "Hanging_Leg_Raise": "hanging-leg-raise",
    "Leg_Press": "leg-press",
    "Face_Pull": "face-pull",
}

EQUIPMENT_WORDS = {"barbell", "dumbbell", "dumbbells", "cable", "machine", "kettlebell", "band", "smith", "lever"}


def fetch(url: str, cache_name: str) -> bytes:
    path = CACHE / cache_name
    if path.exists():
        return path.read_bytes()
    with urllib.request.urlopen(url, timeout=60) as resp:
        data = resp.read()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return data


def tokens(name: str) -> list[str]:
    out = []
    for t in re.split(r"[^a-z0-9]+", name.lower()):
        if not t:
            continue
        if len(t) > 3 and t.endswith("s") and not t.endswith("ss"):
            t = t[:-1]  # einfache Singularform: pushups -> pushup, curls -> curl
        out.append(t)
    return out


def name_keys(name: str) -> tuple[str, str]:
    """Vergleichsschlüssel: vollständiger Name und Name ohne Equipment-Wort am Anfang."""
    toks = tokens(name)
    stripped = toks[1:] if len(toks) > 1 and toks[0] in EQUIPMENT_WORDS else toks
    return "".join(toks), "".join(stripped)


def find_duplicate(name: str, equipment: str, by_full: dict, by_stripped: dict) -> dict | None:
    """Gleicher Name = Dublette. Gleicher Name ohne Equipment-Wort nur bei gleichem Equipment
    (sonst würde z. B. „Barbell Curl" mit „Cable Curl" verschmelzen)."""
    full, stripped = name_keys(name)
    if full in by_full:
        return by_full[full]
    for candidate in (by_full.get(stripped), by_stripped.get(full), by_stripped.get(stripped)):
        if candidate is not None and candidate["equipment"] == equipment:
            return candidate
    return None


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


def fedb_tracking(e: dict) -> str:
    if e.get("category") in ("stretching", "cardio"):
        return "TIME"
    if e.get("equipment") in (None, "body only"):
        return "TIME" if e.get("force") == "static" else "REPS"
    return "WEIGHT_REPS"


def main() -> None:
    print("Lade Workout Guide Manifest …")
    wg = json.loads(fetch(f"{WG_RAW}/manifest.json", f"wg-{WG_SHA}/manifest.json"))
    print("Lade free-exercise-db …")
    fedb = json.loads(fetch(f"{FEDB_RAW}/dist/exercises.json", f"fedb-{FEDB_SHA}/exercises.json"))

    def frame1(e: dict) -> tuple[str, bytes]:
        path = next(f["path"] for f in e["frames"] if f["index"] == 1)
        if not path.endswith(".svg"):
            raise SystemExit(f"Frame 1 von {e['slug']} ist kein SVG: {path}")
        return e["slug"], fetch(f"{WG_RAW}/{path}", f"wg-{WG_SHA}/{path}")

    print(f"Lade {len(wg)} SVG-Frames …")
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        svgs = dict(pool.map(frame1, wg))
    license_text = fetch(f"{WG_RAW}/LICENSE-ASSETS", f"wg-{WG_SHA}/LICENSE-ASSETS")

    exercises: list[dict] = []
    by_full: dict[str, dict] = {}
    by_stripped: dict[str, dict] = {}
    by_slug: dict[str, dict] = {}
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
            "merged": [],
        }
        exercises.append(item)
        by_slug[e["slug"]] = item
        full, stripped = name_keys(e["name"])
        by_full.setdefault(full, item)
        by_stripped.setdefault(stripped, item)

    unknown_manual = set(MANUAL_DUPLICATES.values()) - by_slug.keys()
    if unknown_manual:
        print(f"Warnung: manuelle Zuordnung auf unbekannte Slugs: {sorted(unknown_manual)}", file=sys.stderr)

    merged = 0
    fedb_names: set[str] = set()
    for e in sorted(fedb, key=lambda x: x["name"].lower()):
        where = f"free-exercise-db {e['id']}"
        target = by_slug.get(MANUAL_DUPLICATES.get(e["id"], ""))
        if target is None:
            target = find_duplicate(e["name"], map_equipment(e.get("equipment"), where), by_full, by_stripped)
        if target is not None:
            target["merged"].append(f"fedb:{e['id']}")  # Dublette: Workout Guide hat Vorrang
            merged += 1
            continue
        norm = "".join(tokens(e["name"]))
        if norm in fedb_names:
            continue
        fedb_names.add(norm)
        images = e.get("images") or []
        exercises.append({
            "id": f"fedb:{e['id']}",
            "name": e["name"],
            "source": "FREE_DB",
            "image": f"{FEDB_RAW}/exercises/{images[0]}" if images else None,
            "primary": map_muscles(e.get("primaryMuscles", []), where),
            "secondary": map_muscles(e.get("secondaryMuscles", []), where),
            "equipment": map_equipment(e.get("equipment"), where),
            "tracking": fedb_tracking(e),
            "merged": [],
        })

    for item in exercises:
        if not item["merged"]:
            del item["merged"]

    ids = [x["id"] for x in exercises]
    assert len(ids) == len(set(ids)), "Doppelte IDs"

    payload = json.dumps(exercises, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    version = hashlib.sha256(payload.encode()).hexdigest()[:12]
    doc = {
        "version": version,
        "sources": {
            "workoutGuide": {"repo": "github.com/bryllim/workout-guide", "commit": WG_SHA, "license": "CC BY-SA 4.0"},
            "freeExerciseDb": {"repo": "github.com/yuhonas/free-exercise-db", "commit": FEDB_SHA, "license": "Unlicense"},
        },
        "exercises": exercises,
    }

    svg_dir = OUT / "wg"
    if svg_dir.exists():
        for old in svg_dir.glob("*.svg"):
            old.unlink()
    svg_dir.mkdir(parents=True, exist_ok=True)
    for slug, data in svgs.items():
        (svg_dir / f"{slug}.svg").write_bytes(data)  # Dateien unverändert (Lizenz)
    (OUT / "LICENSE-ASSETS-workout-guide.txt").write_bytes(license_text)
    (OUT / "exercises.json").write_text(
        json.dumps(doc, ensure_ascii=False, indent=0, sort_keys=False) + "\n", encoding="utf-8"
    )

    wg_count = sum(1 for x in exercises if x["source"] == "WORKOUT_GUIDE")
    print(f"Fertig: {len(exercises)} Übungen ({wg_count} Workout Guide, {len(exercises) - wg_count} free-exercise-db, "
          f"{merged} Dubletten zusammengeführt), Version {version}")


if __name__ == "__main__":
    main()
