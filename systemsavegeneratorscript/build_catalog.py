#!/usr/bin/env python3
"""
Build a solar-system body catalog for your simulator.

- Pulls asteroids, TNOs, Centaurs, NEOs, comets from JPL SBDB Query API.
- Optionally merges manual bodies (planets, moons, special objects) from CSV.
- Writes a unified body_catalog.csv that your generator script can filter.

Requires: requests
    pip install requests
"""

import csv
import json
import os
import sys
from typing import Dict, List, Any

import requests

SBDB_QUERY_URL = "https://ssd-api.jpl.nasa.gov/sbdb_query.api"

# =======================
# CONFIG YOU CAN TUNE
# =======================

OUTPUT_CATALOG = "body_catalog.csv"
MANUAL_BODIES_FILE = "manual_bodies.csv"  # optional, see notes below

# --- Small-body selection knobs ---
#
# These thresholds are *for the catalog only*.
# You can keep them fairly generous so the catalog is “big”,
# then your simulation generator applies stricter rules (e.g. >= 300 km).

# Asteroids (main belt + Trojans + generic)
ASTEROID_MIN_DIAM_KM = 40.0   # approximate; reduce to get more, increase to get fewer
ASTEROID_MAX_RESULTS = 5000   # safety cap

# Trans-Neptunian objects
TNO_MIN_DIAM_KM = 40.0
TNO_MAX_RESULTS = 5000

# Centaurs
CENTAUR_MIN_DIAM_KM = 30.0
CENTAUR_MAX_RESULTS = 2000

# Near-Earth objects: mostly much smaller; use H instead
NEO_MAX_H = 18.0              # lower = brighter = typically larger
NEO_MAX_RESULTS = 2000

# Comets: keep small but comprehensive enough
COMET_MIN_DIAM_KM = 1.0       # many comets have very uncertain diameters
COMET_MAX_RESULTS = 2000

# Color presets by "kind" in our catalog (you can tweak later)
COLORS_BY_KIND = {
    "planet": (200, 200, 200),
    "dwarf": (220, 210, 190),
    "moon": (200, 200, 200),
    "irregular_moon": (190, 190, 190),
    "asteroid": (200, 200, 200),
    "tno": (220, 180, 220),
    "centaur": (210, 180, 200),
    "neo": (255, 220, 180),
    "comet": (230, 230, 255),
    "sun": (255, 245, 200),
}

# Default color if kind not found
DEFAULT_COLOR = (200, 200, 200)


# =======================
# SBDB QUERY HELPERS
# =======================


def sbdb_query(params: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    Call JPL SBDB Query API with given params and return list of records
    as dicts keyed by field name.

    See API docs:
    https://ssd-api.jpl.nasa.gov/doc/sbdb_query.html
    """
    resp = requests.get(SBDB_QUERY_URL, params=params, timeout=60)
    resp.raise_for_status()
    data = resp.json()

    fields = data.get("fields", [])
    rows = []

    for row in data.get("data", []):
        rows.append({field: row[i] for i, field in enumerate(fields)})

    print(f"  -> got {len(rows)} rows from SBDB")
    return rows


def make_cdata_diameter_ge(min_diam_km: float) -> str:
    """
    Build simple sb-cdata JSON: diameter >= min_diam_km AND diameter defined.
    Returned as JSON string; requests will URL-encode it.
    """
    c = {"AND": [f"diameter|GE|{min_diam_km}", "diameter|DF"]}
    return json.dumps(c, separators=(",", ":"))


def make_cdata_H_le(max_H: float) -> str:
    """
    sb-cdata: H <= max_H AND H defined.
    """
    c = {"AND": [f"H|LE|{max_H}", "H|DF"]}
    return json.dumps(c, separators=(",", ":"))


# =======================
# CATALOG RECORD HELPERS
# =======================


def classify_kind(sb_record: Dict[str, Any]) -> str:
    """
    Map SBDB record to our catalog 'kind'.
    Uses:
      - sb_record['kind'] (an/au/cn/cu)
      - sb_record['class'] (IMB, MBA, TNO, CEN, etc)
      - sb_record['neo'] ('Y'/'N')
    """
    sb_kind = (sb_record.get("kind") or "").lower()   # 'an', 'au', 'cn', 'cu'
    orbit_class = (sb_record.get("class") or "").upper()
    neo_flag = (sb_record.get("neo") or "N").upper()

    base = "asteroid" if sb_kind.startswith("a") else "comet"

    # Comets
    if base == "comet":
        return "comet"

    # Asteroids / small bodies
    if neo_flag == "Y":
        return "neo"

    if orbit_class == "TNO":
        return "tno"
    if orbit_class == "CEN":
        return "centaur"

    # Everything else we treat just as generic asteroid
    return "asteroid"


def pick_color_for_kind(kind: str) -> (int, int, int):
    return COLORS_BY_KIND.get(kind, DEFAULT_COLOR)


def sb_record_to_catalog_row(sb_record: Dict[str, Any]) -> Dict[str, Any]:
    """
    Convert SBDB record into our unified catalog row.

    Catalog columns:
        kind,id,id_type,name,parent_id,parent_name,radius_km,H,R,G,B,always_include
    """
    kind = classify_kind(sb_record)

    # SBDB fields we requested
    full_name = sb_record.get("full_name") or ""
    pdes = sb_record.get("pdes") or ""
    H_val = sb_record.get("H")
    dia = sb_record.get("diameter")

    try:
        H_val = float(H_val) if H_val is not None else ""
    except Exception:
        H_val = ""

    try:
        dia = float(dia) if dia is not None else None
    except Exception:
        dia = None

    radius_km = dia / 2.0 if dia is not None else ""

    R, G, B = pick_color_for_kind(kind)

    # For SBDB small bodies, we treat pdes as the target "id"
    # and use id_type='smallbody' for your Horizons-based code.
    row = {
        "kind": kind,
        "id": pdes,
        "id_type": "smallbody",
        "name": full_name.strip(),
        "parent_id": "",
        "parent_name": "",
        "radius_km": radius_km,
        "H": H_val,
        "R": R,
        "G": G,
        "B": B,
        "always_include": "",   # you can set this later by hand if you want
    }
    return row


# =======================
# SMALL-BODY POPULATIONS
# =======================


def fetch_asteroids() -> List[Dict[str, Any]]:
    """
    Main-belt + Trojans + generic asteroids with diameter >= ASTEROID_MIN_DIAM_KM.
    """
    print(f"Fetching asteroids (diam >= {ASTEROID_MIN_DIAM_KM} km)...")
    cdata = make_cdata_diameter_ge(ASTEROID_MIN_DIAM_KM)
    params = {
        "fields": "full_name,pdes,kind,class,neo,pha,H,diameter,albedo",
        "sb-kind": "a",
        "sb-class": "IMB,MBA,OMB,TJN,AST",
        "sb-cdata": cdata,
        "limit": ASTEROID_MAX_RESULTS,
        "full-prec": "true",
    }
    records = sbdb_query(params)
    return [sb_record_to_catalog_row(r) for r in records]


def fetch_tnos() -> List[Dict[str, Any]]:
    """
    Trans-Neptunian Objects (TNOs) with diameter >= TNO_MIN_DIAM_KM.
    """
    print(f"Fetching TNOs (diam >= {TNO_MIN_DIAM_KM} km)...")
    cdata = make_cdata_diameter_ge(TNO_MIN_DIAM_KM)
    params = {
        "fields": "full_name,pdes,kind,class,neo,pha,H,diameter,albedo",
        "sb-kind": "a",
        "sb-class": "TNO",
        "sb-cdata": cdata,
        "limit": TNO_MAX_RESULTS,
        "full-prec": "true",
    }
    records = sbdb_query(params)
    return [sb_record_to_catalog_row(r) for r in records]


def fetch_centaurs() -> List[Dict[str, Any]]:
    """
    Centaurs with diameter >= CENTAUR_MIN_DIAM_KM.
    """
    print(f"Fetching Centaurs (diam >= {CENTAUR_MIN_DIAM_KM} km)...")
    cdata = make_cdata_diameter_ge(CENTAUR_MIN_DIAM_KM)
    params = {
        "fields": "full_name,pdes,kind,class,neo,pha,H,diameter,albedo",
        "sb-kind": "a",
        "sb-class": "CEN",
        "sb-cdata": cdata,
        "limit": CENTAUR_MAX_RESULTS,
        "full-prec": "true",
    }
    records = sbdb_query(params)
    return [sb_record_to_catalog_row(r) for r in records]


def fetch_neos() -> List[Dict[str, Any]]:
    """
    Near-Earth objects with H <= NEO_MAX_H.
    """
    print(f"Fetching NEOs (H <= {NEO_MAX_H})...")
    cdata = make_cdata_H_le(NEO_MAX_H)
    params = {
        "fields": "full_name,pdes,kind,class,neo,pha,H,diameter,albedo",
        "sb-kind": "a",
        "sb-group": "neo",
        "sb-cdata": cdata,
        "limit": NEO_MAX_RESULTS,
        "full-prec": "true",
    }
    records = sbdb_query(params)
    return [sb_record_to_catalog_row(r) for r in records]


def fetch_comets() -> List[Dict[str, Any]]:
    """
    Comets with diameter >= COMET_MIN_DIAM_KM (where diameter is defined).

    Many comets have no reliable diameter; those will be excluded here,
    but you can always add specific famous comets into manual_bodies.csv.
    """
    print(f"Fetching comets (diam >= {COMET_MIN_DIAM_KM} km)...")
    cdata = make_cdata_diameter_ge(COMET_MIN_DIAM_KM)
    params = {
        "fields": "full_name,pdes,kind,class,neo,pha,H,diameter,albedo",
        "sb-kind": "c",
        "sb-cdata": cdata,
        "limit": COMET_MAX_RESULTS,
        "full-prec": "true",
    }
    records = sbdb_query(params)
    return [sb_record_to_catalog_row(r) for r in records]


# =======================
# MANUAL BODIES (PLANETS / MOONS / SPECIALS)
# =======================


def load_manual_bodies(path: str) -> List[Dict[str, Any]]:
    """
    Load manually curated bodies (planets, moons, special small bodies)
    from CSV if it exists.

    Expected columns (same as catalog):
        kind,id,id_type,name,parent_id,parent_name,radius_km,H,R,G,B,always_include
    """
    if not os.path.exists(path):
        print(f"No manual_bodies.csv found at {path} (this is fine).")
        return []

    print(f"Loading manual bodies from {path}...")
    out: List[Dict[str, Any]] = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            # Normalize types a bit
            row["radius_km"] = float(row["radius_km"]) if row.get("radius_km") else ""
            row["H"] = float(row["H"]) if row.get("H") else ""
            for c in ("R", "G", "B"):
                row[c] = int(row[c]) if row.get(c) else 200
            out.append(row)
    print(f"  -> loaded {len(out)} manual bodies.")
    return out


# =======================
# MAIN
# =======================


def deduplicate_by_id(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    If the same small body appears in multiple queries (e.g. a NEO also in asteroid list),
    keep the first occurrence.
    """
    seen = set()
    uniq = []
    for r in rows:
        key = (r.get("id"), r.get("id_type"))
        if key in seen:
            continue
        seen.add(key)
        uniq.append(r)
    return uniq


def write_catalog(rows: List[Dict[str, Any]], path: str) -> None:
    fieldnames = [
        "kind",
        "id",
        "id_type",
        "name",
        "parent_id",
        "parent_name",
        "radius_km",
        "H",
        "R",
        "G",
        "B",
        "always_include",
    ]
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in rows:
            writer.writerow(r)
    print(f"Wrote catalog: {path} ({len(rows)} rows)")


def main():
    manual = load_manual_bodies(MANUAL_BODIES_FILE)

    small_bodies: List[Dict[str, Any]] = []
    small_bodies.extend(fetch_asteroids())
    small_bodies.extend(fetch_tnos())
    small_bodies.extend(fetch_centaurs())
    small_bodies.extend(fetch_neos())
    small_bodies.extend(fetch_comets())

    small_bodies = deduplicate_by_id(small_bodies)

    all_rows = manual + small_bodies
    write_catalog(all_rows, OUTPUT_CATALOG)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("ERROR while building catalog:", e, file=sys.stderr)
        sys.exit(1)
