import csv, gzip, math, struct, argparse

OBLIQUITY_DEG = 23.439281
EPS = math.radians(OBLIQUITY_DEG)
COS_EPS = math.cos(EPS)
SIN_EPS = math.sin(EPS)

def clamp(x, lo, hi):
    return lo if x < lo else hi if x > hi else x

def bv_to_kelvin(bv: float) -> float:
    # Ballesteros formula (approx): T = 4600 * (1/(0.92(B-V)+1.7) + 1/(0.92(B-V)+0.62))
    # Valid-ish for typical stellar B-V ranges.
    return 4600.0 * (1.0 / (0.92 * bv + 1.7) + 1.0 / (0.92 * bv + 0.62))

def kelvin_to_rgb(T: float):
    # Tanner Helland-ish approximation (expects 1000K..40000K)
    T = clamp(T, 1000.0, 40000.0) / 100.0

    # red
    if T <= 66:
        r = 255
    else:
        r = 329.698727446 * ((T - 60) ** -0.1332047592)

    # green
    if T <= 66:
        g = 99.4708025861 * math.log(T) - 161.1195681661
    else:
        g = 288.1221695283 * ((T - 60) ** -0.0755148492)

    # blue
    if T >= 66:
        b = 255
    elif T <= 19:
        b = 0
    else:
        b = 138.5177312231 * math.log(T - 10) - 305.0447927307

    r = int(clamp(r, 0, 255))
    g = int(clamp(g, 0, 255))
    b = int(clamp(b, 0, 255))
    return r, g, b

def equatorial_xyz_to_engine_dir(x, y, z):
    # 1) equatorial -> ecliptic (rotate about +X)
    xEc = x
    yEc = COS_EPS * y + SIN_EPS * z
    zEc = -SIN_EPS * y + COS_EPS * z

    # 2) ecliptic -> engine (X, Z, Y)
    ex = xEc
    ey = zEc
    ez = yEc

    # normalize
    L = math.sqrt(ex*ex + ey*ey + ez*ez)
    if L < 1e-12:
        return None
    return (ex / L, ey / L, ez / L)

def open_csv(path):
    if path.endswith(".gz"):
        return gzip.open(path, "rt", newline="", encoding="utf-8")
    return open(path, "r", newline="", encoding="utf-8")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", required=True, help="hygdata_v3.csv or .csv.gz")
    ap.add_argument("--out", dest="outp", required=True, help="output .bin")
    ap.add_argument("--max-stars", type=int, default=10000, help="keep brightest N")
    ap.add_argument("--max-mag", type=float, default=9.0, help="discard stars dimmer than this")
    args = ap.parse_args()

    stars = []

    with open_csv(args.inp) as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                mag = float(row.get("mag", "") or "999")
                if mag > args.max_mag:
                    continue

                x = float(row.get("x", "") or "0")
                y = float(row.get("y", "") or "0")
                z = float(row.get("z", "") or "0")
                d = equatorial_xyz_to_engine_dir(x, y, z)
                if d is None:
                    continue

                # Color index (B-V) is "ci" in HYG
                ci_raw = row.get("ci", "")
                if ci_raw == "" or ci_raw is None:
                    bv = 0.65  # fallback ~sun-ish
                else:
                    bv = float(ci_raw)
                    bv = clamp(bv, -0.4, 2.0)  # reasonable range

                T = bv_to_kelvin(bv)
                r, g, b = kelvin_to_rgb(T)

                argb = (0xFF << 24) | (r << 16) | (g << 8) | b

                stars.append((mag, d[0], d[1], d[2], argb))
            except Exception:
                continue

    # keep brightest (lowest mag)
    stars.sort(key=lambda s: s[0])
    stars = stars[:args.max_stars]

    with open(args.outp, "wb") as out:
        out.write(b"STAR")
        out.write(struct.pack("<i", 1))                 # version
        out.write(struct.pack("<i", len(stars)))        # count
        for mag, dx, dy, dz, argb in stars:
            out.write(struct.pack("<ffffI", dx, dy, dz, mag, argb))

    print(f"Wrote {len(stars)} stars to {args.outp}")

if __name__ == "__main__":
    main()
