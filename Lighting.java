import java.lang.Math;

public final class Lighting {
    private Lighting() {}

    // --- Tunables (start here, tune here) ---
    public static float PLANET_AMBIENT = 0.0f;

    // Solar falloff shaping to keep outer planets visible while still dimmer.
    public static float SOLAR_MIN   = 0.1f; // never fully black
    public static float SOLAR_MAX   = 1.25f; // allow slight overbright near sun

    // Rings: optional incidence shaping (0 = no incidence term)
    public static boolean USE_RING_INCIDENCE = false;
    public static float RING_INCIDENCE_GAMMA = 0.7f; // 0.5–1.2
    public static float RING_INCIDENCE_MIN   = 0.25f; // ensures rings don't vanish edge-on

    // 1 AU in km
    public static final double AU_KM = 149_597_870.7;

    /** Solar illumination factor at a world position (relative to 1 AU), shaped & clamped. */
    public static float solarIllumAt(double worldX, double worldY, double worldZ,
                                     double sunX, double sunY, double sunZ) {
        double dx = worldX - sunX;
        double dy = worldY - sunY;
        double dz = worldZ - sunZ;
        double dist2 = dx*dx + dy*dy + dz*dz;
        if (dist2 < 1e-12) dist2 = 1e-12;

        double auUnits = AU_KM / SimulationView.SCALE_KM_PER_UNIT;
        double s = (auUnits * auUnits) / dist2;     // inverse-square relative to 1 AU
        if (s < SOLAR_MIN) s = SOLAR_MIN;
        if (s > SOLAR_MAX) s = SOLAR_MAX;
        return (float)s;
    }

    /** Planet intensity (0..1) from Lambert + solar factor. */
    public static float planetIntensity(float lambert01, float solar) {
        if (lambert01 < 0f) lambert01 = 0f;
        if (lambert01 > 1f) lambert01 = 1f;

        float amb = PLANET_AMBIENT;
        float intensity = amb + (1f - amb) * lambert01 * solar;
        if (intensity < 0f) intensity = 0f;
        if (intensity > 1f) intensity = 1f;
        return intensity;
    }

    /**
     * Optional ring incidence term: rings get dimmer when sun grazes the ring plane.
     * ringNormal must be unit length. Returns ~[RING_INCIDENCE_MIN..1].
     */
    public static float ringIncidence(double ringNx, double ringNy, double ringNz,
                                      double planetX, double planetY, double planetZ,
                                      double sunX, double sunY, double sunZ) {
        if (!USE_RING_INCIDENCE) return 1.0f;

        double lx = sunX - planetX;
        double ly = sunY - planetY;
        double lz = sunZ - planetZ;
        double len = Math.sqrt(lx*lx + ly*ly + lz*lz);
        if (len < 1e-12) return 1.0f;
        lx /= len; ly /= len; lz /= len;

        double inc = Math.abs(lx*ringNx + ly*ringNy + lz*ringNz); // 0..1
        inc = Math.pow(inc, RING_INCIDENCE_GAMMA);

        double scaled = RING_INCIDENCE_MIN + (1.0 - RING_INCIDENCE_MIN) * inc;
        return (float)scaled;
    }
}
