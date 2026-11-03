#!/usr/bin/env python3
"""Baut den Übungskatalog der App aus Workout Guide (fest gepinnter Commit).

Quelle (Commit-SHA gepinnt, damit der Import reproduzierbar ist):
  * Workout Guide (Bryl Lim, Grafiken auf Basis von Everkinetic, CC BY-SA 4.0); Frame 1 als SVG wird offline
    mitgebündelt. free-exercise-db wurde am 3.11.2026 entfernt (Fotos passten nicht zum Design), eigene
    Übungen legt man in der App an (auf Wunsch per KI ausgefüllt).

Ergebnis:
  shared/src/commonMain/composeResources/files/catalog/exercises.json
  shared/src/commonMain/composeResources/files/catalog/wg/<slug>.svg   (unverändert aus Workout Guide)
  shared/src/commonMain/composeResources/files/catalog/LICENSE-ASSETS-workout-guide.txt
(Compose Resources: gemeinsam für Android und iOS.)

Aufruf (aus dem Repo-Root):  python3 tools/catalog/import_catalog.py
Nur Python-Standardbibliothek. Downloads werden unter tools/catalog/.cache zwischengespeichert.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
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
    for slug, data in svgs.items():
        (svg_dir / f"{slug}.svg").write_bytes(data)  # Dateien unverändert (Lizenz)
    (OUT / "LICENSE-ASSETS-workout-guide.txt").write_bytes(license_text)
    (OUT / "exercises.json").write_text(
        json.dumps(doc, ensure_ascii=False, indent=0, sort_keys=False) + "\n", encoding="utf-8"
    )

    print(f"Fertig: {len(exercises)} Übungen (Workout Guide), Version {version}")


if __name__ == "__main__":
    main()
