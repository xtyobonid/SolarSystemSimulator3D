package psp.desktop.app;

/**
 * Simple render-only body for now (we'll later back this with core.Body objects).
 * Position is in world units (double precision).
 * Radius is world units (float).
 * Color is linear-ish RGB 0..1.
 */
public final class RenderBody {
    public double x, y, z;
    public float radius;

    public float r, g, b;

    /** If true, body is self-illuminated (Sun). */
    public boolean emissive;

    public RenderBody(double x, double y, double z, float radius, float r, float g, float b, boolean emissive) {
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.r = r; this.g = g; this.b = b;
        this.emissive = emissive;
    }
}
