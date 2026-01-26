import java.awt.*;
import java.awt.geom.Point2D;
import java.io.*;

public final class Starfield {

    private final float[] dirX, dirY, dirZ;
    private final float[] mag;
    private final Color[] color;
    public final int count;

    public float minPointRadiusPx = 0.5f;
    public float resolveStartPx   = 0.4f;
    public float resolveEndPx     = 1.0f;
    public float eye_adaptation   = 12.0f;

    // exposure on magnitude->brightness
    public float magExposure = 3.0f;

    // final clamp
    public float alphaMin = 0.001f;
    public float alphaMax = 0.98f;

    public double starDist = 1.0e9;    // world units

    private float maxStarAlpha = 0f;

    // --- Debug (optional) ---
    public boolean debugStats = false;
    private long lastStatsNs = 0L;

    // Reuse (no per-star allocs)
    private final double[] cam4 = new double[4];
    private final Point2D.Double screen = new Point2D.Double();

    // Alpha cache (avoid AlphaComposite.getInstance each star)
    private static final AlphaComposite[] ALPHA = new AlphaComposite[256];
    static {
        for (int i = 0; i < 256; i++) {
            ALPHA[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 255f);
        }
    }

    private Starfield(int count) {
        this.count = count;
        this.dirX = new float[count];
        this.dirY = new float[count];
        this.dirZ = new float[count];
        this.mag  = new float[count];
        this.color = new Color[count];
    }

    public static Starfield loadFromFile(java.nio.file.Path path) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(path.toFile()))) {
            return loadFromStream(is, path.toString());
        }
    }

    // Binary format:
    // 4 bytes: 'S''T''A''R'
    // int32 LE: version (1)
    // int32 LE: count
    // then count records: float32 dx,dy,dz,mag ; uint32 argb  (all LE)
    public static Starfield loadFromResource(String resourcePath) throws IOException {
        InputStream is = Starfield.class.getResourceAsStream(resourcePath);
        if (is == null) throw new FileNotFoundException("Missing resource: " + resourcePath);
        try (InputStream bis = new BufferedInputStream(is)) {
            return loadFromStream(bis, resourcePath);
        }
    }

    private static Starfield loadFromStream(InputStream is, String label) throws IOException {
        try (DataInputStream dis = new DataInputStream(is)) {
            int m0 = dis.readUnsignedByte();
            int m1 = dis.readUnsignedByte();
            int m2 = dis.readUnsignedByte();
            int m3 = dis.readUnsignedByte();
            if (m0 != 'S' || m1 != 'T' || m2 != 'A' || m3 != 'R') {
                throw new IOException("Bad starfield header in " + label + " (expected 'STAR')");
            }

            int version = Integer.reverseBytes(dis.readInt());
            if (version != 1) throw new IOException("Unsupported starfield version: " + version);

            int n = Integer.reverseBytes(dis.readInt());
            if (n <= 0 || n > 200000) throw new IOException("Unreasonable star count: " + n);

            Starfield sf = new Starfield(n);

            for (int i = 0; i < n; i++) {
                sf.dirX[i] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                sf.dirY[i] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                sf.dirZ[i] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                sf.mag[i]  = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                int argb = Integer.reverseBytes(dis.readInt());
                sf.color[i] = new java.awt.Color(argb, true);
            }

            return sf;
        }
    }


    // Relative flux from magnitude (Pogson)
    private static float fluxFromMagnitude(float m) {
        return (float) java.lang.Math.pow(10.0, -0.4 * m);
    }
    private static float clampf(float x, float lo, float hi) {
        return (x < lo) ? lo : (x > hi) ? hi : x;
    }
    private static float smoothstep(float e0, float e1, float x) {
        float t = clampf((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // Inverse hyperbolic sine
    private static double asinh(double x) {
        return java.lang.Math.log(x + Math.sqrt(x * x + 1.0));
    }

    private static float brightnessFromMag(float mag) {
        return (float)java.lang.Math.pow(10.0, -0.4 * mag);
    }

    // Perceptual brightness mapping for dark-adapted human vision
    private float perceptualMap(float b) {
        // b is expected in [0,1]
        b = clampf(b, 0f, 1f);

        // asinh tone mapping (astronomy standard)
        double k = eye_adaptation;
        return (float)(asinh(k * b) / asinh(k));
    }

    public void draw(Graphics2D g2, Frustum frustum, int viewW, int viewH) {
        Composite oldComp = g2.getComposite();
        Color oldColor = g2.getColor();

        int drawn = 0;
        float minM =  1e9f, maxM = -1e9f;
        float minA =  1e9f, maxA = -1e9f;
        double sumA = 0.0;

        int lastAlphaIdx = -1;
        Color lastC = null;

        double cx = frustum.cameraX;
        double cy = frustum.cameraY;
        double cz = frustum.cameraZ;

        for (int i = 0; i < count; i++) {
            // Place star at constant distance along its direction from the camera
            double wx = cx + dirX[i] * starDist;
            double wy = cy + dirY[i] * starDist;
            double wz = cz + dirZ[i] * starDist;

            frustum.worldToCameraSpaceDirect(wx, wy, wz, cam4);

            // Use your non-alloc projection overload
            if (!frustum.project3DTo2D(cam4[0], cam4[1], cam4[2], viewW, viewH, screen)) {
                continue;
            }

            float m = mag[i];

            // If the catalog includes “sun record” (-26.7), skip it.
            if (m < -5.0f) continue;

            // pixels-per-radian using current camera FOV
            float fovRad = (float)Math.toRadians(frustum.fov);
            float pixelsPerRad = viewW / fovRad;

            // Angular radius in pixels: r = (R / d) * pixelsPerRad
//            float dKm = distKm[i];
//            float rKm = radiusKm[i];
            float rAngular = 0f;
//            if (dKm > 0f && rKm > 0f) {
//                rAngular = (rKm / dKm) * pixelsPerRad;
//            }

            // magnitude brightness (scaled)
            float bMag = brightnessFromMag(m) * magExposure;
            bMag = clampf(bMag, 0f, 1f);

            // blend based on resolvability
            float tRes = smoothstep(resolveStartPx, resolveEndPx, rAngular);

            // pixel radius transitions from point sprite to true angular size
            float pixelRadius = lerp(minPointRadiusPx, rAngular, tRes);

            // brightness transitions from magnitude-driven to “fully bright disk” when resolved
            float brightness = lerp(bMag, 1.0f, tRes);

            // convert pixelRadius to a single-pixel coverage factor (avoids drawing bigger stars)
            float coverage = (float)(Math.PI * pixelRadius * pixelRadius);
            coverage = clampf(coverage, 0f, 1f);

            float starExposure = 1.0f;
            if (maxStarAlpha > 0f) {
                starExposure = 1.0f / maxStarAlpha;
            }
            starExposure = Math.min(starExposure, 2.0f); // safety

            // final alpha
            float perceptualBrightness = perceptualMap(brightness);
            float a = perceptualBrightness * coverage;

            maxStarAlpha = Math.max(maxStarAlpha, a);

            a *= starExposure;
            a = clampf(a, alphaMin, alphaMax);



            if (a < alphaMin) a = alphaMin;
            if (a > alphaMax) a = alphaMax;

            int alphaIdx = (int) (a * 255f + 0.5f);
            if (alphaIdx < 0) alphaIdx = 0;
            if (alphaIdx > 255) alphaIdx = 255;

            if (alphaIdx != lastAlphaIdx) {
                g2.setComposite(ALPHA[alphaIdx]);
                lastAlphaIdx = alphaIdx;
            }

            Color c = color[i];
            if (c != lastC) {
                g2.setColor(c);
                lastC = c;
            }

            int x = (int) Math.round(screen.x);
            int y = (int) Math.round(screen.y);

            drawn++;
            if (m < minM) minM = m;
            if (m > maxM) maxM = m;
            if (a < minA) minA = a;
            if (a > maxA) maxA = a;
            sumA += a;

            g2.fillRect(x, y, 1, 1);
        }

        if (debugStats) {
            long now = System.nanoTime();
            if (now - lastStatsNs >= 1_000_000_000L) {
                double avgA = (drawn > 0) ? (sumA / drawn) : 0.0;
                System.out.println(String.format(
                        "STARS: drawn=%d  mag=[%.2f..%.2f]  alpha=[%.4f..%.4f]  avg=%.4f",
                        drawn, minM, maxM, minA, maxA, avgA
                ));
                lastStatsNs = now;
            }
        }

        g2.setComposite(oldComp);
        g2.setColor(oldColor);
    }
}
