from astroquery.jplhorizons import Horizons
from astropy.time import Time
import csv

# =======================
# CONFIG
# =======================

SCALE_KM_PER_UNIT = 100.0  # 1 sim unit = 100 km
OUTFILE = "solar_system_elliptical.save"
CATALOG_FILE = "body_catalog.csv"

# Epoch: now, in TDB Julian date (what Horizons expects)
EPOCH = Time.now()
EPOCH_JD = EPOCH.tdb.jd

AU_KM = 149_597_870.7

# ---- Selection thresholds (tune these as you like) ----

MIN_DIAM_BIG_OBJECT_KM = 300.0   # "All objects with diameter ≥ 300 km"
MIN_DIAM_MOON_KM       = 150.0   # "All moons with diameter ≥ 150 km"
MIN_DIAM_ASTEROID_KM   = 200.0   # "All asteroids with diameter ≥ 200 km"
MAX_TNO_ABS_MAG        = 3.5     # "Main TNO dwarf candidates: H ≤ 3.5"


# =======================
# HORIZONS HELPERS
# =======================

def fetch_elements(body_id, center_id):
    """
    Fetch osculating elements (a,e,i,Omega,w,M,P) for a body with a
    numeric Horizons/NAIF ID, relative to a numeric center_id (e.g. '10' Sun, '399' Earth).
    """
    if center_id == "10":  # Sun
        loc = "@sun"
    else:
        loc = f"500@{center_id}"

    obj = Horizons(id=body_id, id_type='id', location=loc, epochs=EPOCH_JD)
    el = obj.elements(refsystem='ICRF', refplane='ecliptic')

    row = el[0]
    a_au  = float(row['a'])
    e     = float(row['e'])
    i_deg = float(row['incl'])
    Om_deg= float(row['Omega'])
    w_deg = float(row['w'])
    M_deg = float(row['M'])
    P_days= float(row['P'])

    # scale semi-major axis
    a_km    = a_au * AU_KM
    a_units = a_km / SCALE_KM_PER_UNIT

    return a_units, e, i_deg, Om_deg, w_deg, M_deg, P_days


def fetch_elements_smallbody(target_name):
    """
    Fetch osculating elements for an asteroid/comet/TNO around the Sun,
    using Horizons 'smallbody' IDs like '1', '4', '134340', '67P', etc.
    """

    target_name = str(target_name).strip()
    # quick sanity check
    if target_name.isdigit() and int(target_name) > 875150:
        raise ValueError(f"Suspicious smallbody id '{target_name}' (too large); "
                         f"did you accidentally use an SPK ID instead of the IAU number?")
    
    obj = Horizons(id=target_name, id_type='smallbody',
                   location='@sun', epochs=EPOCH_JD)
    try:
        el = obj.elements(refsystem='ICRF', refplane='ecliptic')
    except ValueError as err:
        # If it's a known tricky case like Halley, you *could* special-case it:
        if 'Ambiguous target name' in str(err) and target_name.upper() in ('1P', '1P/HALLEY'):
            obj = Horizons(id='90000030', id_type='id',
                           location='@sun', epochs=EPOCH_JD)
            el = obj.elements(refsystem='ICRF', refplane='ecliptic')
        else:
            raise
    row = el[0]

    a_au  = float(row['a'])
    e     = float(row['e'])
    i_deg = float(row['incl'])
    Om_deg= float(row['Omega'])
    w_deg = float(row['w'])
    M_deg = float(row['M'])
    P_days= float(row['P'])

    a_km    = a_au * AU_KM
    a_units = a_km / SCALE_KM_PER_UNIT

    return a_units, e, i_deg, Om_deg, w_deg, M_deg, P_days


def fetch_radius_smallbody(target_id, fallback_radius_km):
    """
    Fetch physical radius from Horizons if available; otherwise use fallback.
    """
    try:
        obj = Horizons(id=target_id, id_type='smallbody',
                       location='@sun', epochs=EPOCH_JD)
        eph = obj.ephemerides()
        if 'rad' in eph.colnames:
            rad_km = float(eph[0]['rad'])
            if rad_km > 0:
                return rad_km / SCALE_KM_PER_UNIT
    except Exception as e:
        print(f"[WARN] Could not fetch radius for {target_id}: {e}")

    return fallback_radius_km / SCALE_KM_PER_UNIT


# =======================
# CATALOG LOADING & FILTERING
# =======================

def load_catalog(path=CATALOG_FILE):
    """
    Read body_catalog.csv into a list of dicts.
    Expected columns (see sample below):
        kind,id,id_type,name,parent_id,parent_name,radius_km,H,R,G,B,always_include
    """
    bodies = []
    with open(path, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # Normalize numeric fields
            row['radius_km'] = float(row['radius_km']) if row.get('radius_km') else 0.0
            row['H'] = float(row['H']) if row.get('H') else None

            for c in ('R', 'G', 'B'):
                row[c] = int(row[c]) if row.get(c) else 200

            ai = (row.get('always_include') or "").strip().lower()
            row['always_include'] = ai in ("1", "true", "yes", "y")

            # Default for id_type if missing
            row['id_type'] = (row.get('id_type') or 'id').strip()

            # Stringify ids for Horizons
            row['id'] = str(row['id']).strip()
            row['parent_id'] = str(row.get('parent_id') or "").strip()

            bodies.append(row)
    return bodies


def should_include(body):
    """
    Decide if a body from the catalog should be included,
    according to your rules + thresholds.
    """
    kind = body['kind'].lower()
    radius_km = body['radius_km']
    diam_km = 2.0 * radius_km
    H = body['H']
    always = body['always_include']

    # Explicit overrides: e.g. Phobos, Charon, notable NEOs, representative comets
    if always:
        return True

    # 1. All objects with diameter ≥ 300 km
    if diam_km >= MIN_DIAM_BIG_OBJECT_KM and kind in (
        'planet', 'dwarf', 'tno', 'centaur', 'asteroid'
    ):
        return True

    # 2. Moons ≥ 150 km + curated irregulars
    if kind in ('moon', 'irregular_moon'):
        if diam_km >= MIN_DIAM_MOON_KM:
            return True
        # smaller moons only if explicitly flagged in catalog via always_include
        return False

    # 3. Asteroids ≥ 200 km
    if kind == 'asteroid' and diam_km >= MIN_DIAM_ASTEROID_KM:
        return True

    # 4. IAU dwarfs + main candidates: TNOs with H ≤ MAX_TNO_ABS_MAG
    if kind in ('tno', 'dwarf') and H is not None and H <= MAX_TNO_ABS_MAG:
        return True

    # 5. Representative comets: choose via always_include in catalog
    if kind == 'comet':
        # If you want "big" comets by some size proxy you could do that here,
        # but usually you'll curate them and mark always_include = true.
        return False

    # 6. NEOs with notable orbits or spacecraft visits:
    # also curated via always_include
    if kind == 'neo':
        return False

    return False


def split_by_kind(bodies):
    """
    Take the catalog, filter by should_include, and split into
    planets/dwarfs, moons, and smallbodies (asteroids, TNOs, comets, centaurs, NEO).
    """
    planets = []
    moons = []
    smallbodies = []

    for b in bodies:
        if not should_include(b):
            continue

        k = b['kind'].lower()
        if k in ('planet', 'dwarf'):
            planets.append(b)
        elif k in ('moon', 'irregular_moon'):
            moons.append(b)
        else:  # asteroids, tno, centaur, comet, neo, etc.
            smallbodies.append(b)

    return planets, moons, smallbodies


# =======================
# MAIN GENERATOR
# =======================

def generate_save():
    all_bodies = load_catalog()
    planets, moons, smallbodies = split_by_kind(all_bodies)

    print(f"Selected {len(planets)} planets/dwarfs, {len(moons)} moons, {len(smallbodies)} small bodies")

    with open(OUTFILE, "w", encoding="utf-8") as f:
        # simulation time at epoch = 0 ns
        f.write("0\n")
        # comment with epoch
        f.write(f"# Epoch UTC: {EPOCH.utc.iso}\n")

        # --- PLANETS & DWARFS (orbiting the Sun) ---
        f.write(str(len(planets)) + "\n")
        for b in planets:
            name = b['name']
            body_id = b['id']
            id_type = b['id_type']
            R, G, B = b['R'], b['G'], b['B']
            radius_units = b['radius_km'] / SCALE_KM_PER_UNIT

            # planets/dwarfs orbit Sun (center_id='10')
            if id_type == 'id':
                a, e, i_deg, Om_deg, w_deg, M_deg, P_days = fetch_elements(body_id, center_id="10")
            else:  # 'smallbody' (e.g. Ceres as '1', Eris as '136199', etc.)
                a, e, i_deg, Om_deg, w_deg, M_deg, P_days = fetch_elements_smallbody(body_id)

            x0 = y0 = z0 = 0.0
            line = (
                f"{x0} {y0} {z0} "
                f"{radius_units:.6f} {R} {G} {B} "
                f"{a:.6f} {e:.8f} {i_deg:.6f} {Om_deg:.6f} {w_deg:.6f} {M_deg:.6f} {P_days:.8f} "
                f"{name}\n"
            )
            f.write(line)

        # --- MOONS (orbiting parent planet) ---
        f.write(str(len(moons)) + "\n")
        for b in moons:
            name = b['name']
            body_id = b['id']
            parent_name = b['parent_name']
            parent_id = b['parent_id']
            R, G, B = b['R'], b['G'], b['B']
            radius_units = b['radius_km'] / SCALE_KM_PER_UNIT

            # Moons always use numeric 'id' here
            a, e, i_deg, Om_deg, w_deg, M_deg, P_days = fetch_elements(body_id, center_id=parent_id)

            x0 = y0 = z0 = 0.0
            line = (
                f"{x0} {y0} {z0} "
                f"{radius_units:.6f} {R} {G} {B} "
                f"{a:.6f} {e:.8f} {i_deg:.6f} {Om_deg:.6f} {w_deg:.6f} {M_deg:.6f} {P_days:.8f} "
                f"{name} {parent_name}\n"
            )
            f.write(line)

        # --- SMALL BODIES (asteroids, TNOs, comets, centaurs, NEOs) ---
        f.write(str(len(smallbodies)) + "\n")
        for b in smallbodies:
            display_name = b['name']
            target_id = str(b['id']).strip()
            id_type = (b.get('id_type') or 'smallbody').strip()
            fallback_rad_km = b['radius_km']
            R, G, B = b['R'], b['G'], b['B']

            if id_type == 'id':
                # Use the same fetch_elements() you use for planets/moons (center = Sun)
                a, e, i_deg, Om_deg, w_deg, M_deg, P_days = fetch_elements(target_id, center_id="10")
                radius_units = fallback_rad_km / SCALE_KM_PER_UNIT
            else:
                # Default path: SBDB-style smallbody id (e.g. "1", "433", "67P")
                a, e, i_deg, Om_deg, w_deg, M_deg, P_days = fetch_elements_smallbody(target_id)
                radius_units = fetch_radius_smallbody(target_id, fallback_rad_km)

            x0 = y0 = z0 = 0.0
            line = (
                f"{x0} {y0} {z0} "
                f"{radius_units:.6f} {R} {G} {B} "
                f"{a:.6f} {e:.8f} {i_deg:.6f} {Om_deg:.6f} {w_deg:.6f} {M_deg:.6f} {P_days:.8f} "
                f"{display_name}\n"
            )
            f.write(line)

    print(f"Wrote {OUTFILE}")
    print(f"Epoch used: {EPOCH.utc.iso} (UTC), JD={EPOCH_JD:.8f}")


if __name__ == "__main__":
    generate_save()
