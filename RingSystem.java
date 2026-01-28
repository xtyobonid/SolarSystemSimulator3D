import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RingSystem {
    private static class Particle {
        double radius;     // distance from planet center, in sim units
        double baseAngle;  // initial angle around ring
        double height;     // small up/down offset
        float brightness;  // 0..1, used to vary opacity or color
        double sizeUnits;   // physical radius of particle in world/sim units
    }
    
    public static class RingBand {
        public final double innerRadius;
        public final double outerRadius;
        public final int particleCount;
        public final Color color;

        // Main visual knob: "how opaque is this band", independent of particleCount (particleCount is quality)
        public final float opticalDepth;

        public final float jitter; // thickness/noise (optional)

        public RingBand(double innerRadius, double outerRadius, int particleCount, Color color, float opticalDepth) {
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.particleCount = particleCount;
            this.color = color;
            this.opticalDepth = opticalDepth;
            this.jitter = 1.0f;
        }
    }


    private final Planet planet;
    private final double angularSpeed; // radians per second

    // Ring plane basis:
    private final Vector3d normal;   // ring plane normal (unit)
    private final Vector3d right;    // axis in ring plane
    private final Vector3d forward;  // axis in ring plane
    
    public float ringShadowBrightness = 0.15f; // 0.0 = black, 1.0 = no shadow
    public double ringShadowSoftness = 0;   // as a fraction of planet radius (0 = hard edge)
    
	private final ArrayList<RingBand> bands = new ArrayList<>();
	private final ArrayList<Particle[]> bandParticles = new ArrayList<>();
	private final ArrayList<Float> bandWeights = new ArrayList<>();
	
	private static final java.awt.AlphaComposite[] COMP = new java.awt.AlphaComposite[256];
	private static java.awt.AlphaComposite comp(float a) {
	    int i = (int)(a * 255f + 0.5f);
	    if (i < 0) i = 0;
	    if (i > 255) i = 255;
	    java.awt.AlphaComposite c = COMP[i];
	    if (c == null) COMP[i] = c = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, i / 255f);
	    return c;
	}

	private final double[] camTmp = new double[4];
	private final Point2D.Double screenTmp = new Point2D.Double();
	private final Point2D.Double planetScreenTmp = new Point2D.Double();
    
	public RingSystem(Planet planet, double angularSpeed, Vector3d normalDir) {
	    this.planet = planet;
	    this.angularSpeed = angularSpeed;

	    // Build ring plane basis
	    this.normal = new Vector3d(normalDir.x, normalDir.y, normalDir.z).normalize();

	    Vector3d ref = (Math.abs(this.normal.y) < 0.9)
	            ? new Vector3d(0.0, 1.0, 0.0)
	            : new Vector3d(1.0, 0.0, 0.0);

	    this.right = ref.cross(this.normal).normalize();
	    this.forward = this.normal.cross(this.right).normalize();
	}

	public void draw(Graphics g, Frustum frustum, long simulationTime) {
	    if (!(g instanceof Graphics2D g2)) return;

        if (bands.isEmpty()) return;

	    double px = planet.getX();
	    double py = planet.getY();
	    double pz = planet.getZ();

	    Star sun = planet.getRootStar();
	    if (sun == null) return;
	    double sx = sun.getX(), sy = sun.getY(), sz = sun.getZ();
	    
	    float illum = Lighting.solarIllumAt(px, py, pz, sx, sy, sz);
	    float incidence = Lighting.ringIncidence(normal.x, normal.y, normal.z, px, py, pz, sx, sy, sz);

	    // Project planet center once
	    frustum.worldToCameraSpaceDirect(px, py, pz, camTmp);
	    boolean planetProjected = frustum.project3DTo2D(camTmp[0], camTmp[1], camTmp[2],
	            SimulationView.VIEW_WIDTH, SimulationView.VIEW_HEIGHT, planetScreenTmp);
	    boolean canOcclude = planetProjected;

	    // If planet is behind camera, still draw rings, just skip occlusion
	    Point2D.Double planetScreen = planetProjected ? planetScreenTmp : null;

	    // Distance to camera
	    double dx = px - frustum.cameraX;
	    double dy = py - frustum.cameraY;
	    double dz = pz - frustum.cameraZ;
	    double distanceToCamera = Math.sqrt(dx*dx + dy*dy + dz*dz);
	    if (distanceToCamera <= 0.0) return;

	    // Planet screen radius (only if projectable)
	    double planetScreenRadius = 0.0;
	    if (planetProjected) {
	        planetScreenRadius = Frustum.computePixelRadiusByProjection(frustum, px, py, pz,
	                planet.getRadius(), planetScreen);
	    }

	    double tSeconds = simulationTime / 1_000_000_000.0;

	    Color oldColor = g2.getColor();
	    java.awt.Composite oldComp = g2.getComposite();

	    // Precompute planet->camera vector (primitives)
	    double pcx = frustum.cameraX - px;
	    double pcy = frustum.cameraY - py;
	    double pcz = frustum.cameraZ - pz;

	    // Precompute basis components (avoid Vector3d alloc)
	    double rx = right.x, ry = right.y, rz = right.z;
	    double fx = forward.x, fy = forward.y, fz = forward.z;
	    double nx = normal.x, ny = normal.y, nz = normal.z;

	    // pixelsPerUnit from planet screen radius if possible; otherwise approximate
	    double pixelsPerUnit = 0.0;
	    if (planetProjected && planet.getRadius() > 0.0) {
	        pixelsPerUnit = planetScreenRadius / planet.getRadius();
	    }

	    if (pixelsPerUnit <= 0.0) {
	        // Approximate pixels-per-unit at this distance using vertical FOV
	        double fovRad = Math.toRadians(frustum.fov);
	        pixelsPerUnit = (SimulationView.VIEW_HEIGHT * 0.5) / (Math.tan(fovRad * 0.5) * distanceToCamera);
	    }
	    
	    final int W = SimulationView.VIEW_WIDTH;
	    final int H = SimulationView.VIEW_HEIGHT;

	    for (int bi = 0; bi < bands.size(); bi++) {
	        RingBand band = bands.get(bi);
	        Particle[] particles = bandParticles.get(bi);
	        float bandWeight = bandWeights.get(bi);
	        
	        // Optional coarse cull using band outer radius
	        double maxVisible = band.outerRadius * 100.0;
	        if (distanceToCamera > maxVisible) continue;

	        g2.setColor(band.color);

	        for (Particle p : particles) {
	            double angleAround = p.baseAngle + angularSpeed * tSeconds;
	            double cosA = Math.cos(angleAround);
	            double sinA = Math.sin(angleAround);

	            double radialX = p.radius * cosA;
	            double radialZ = p.radius * sinA;

	            // offset = right*radialX + forward*radialZ + normal*height
	            double ox = rx * radialX + fx * radialZ + nx * p.height;
	            double oy = ry * radialX + fy * radialZ + ny * p.height;
	            double oz = rz * radialX + fz * radialZ + nz * p.height;

	            double wx = px + ox;
	            double wy = py + oy;
	            double wz = pz + oz;
	            
	            // Project particle (non-alloc)
	            frustum.worldToCameraSpaceDirect(wx, wy, wz, camTmp);
	            if (!frustum.project3DTo2D(camTmp[0], camTmp[1], camTmp[2], W, H, screenTmp)) continue;

	            // Front/back classification relative to planet: dot(planetToParticle, planetToCamera)
	            double ptx = wx - px;
	            double pty = wy - py;
	            double ptz = wz - pz;
	            double dot = ptx*pcx + pty*pcy + ptz*pcz;
	            boolean isFrontSide = (dot > 0.0);

	            // Back-side occlusion under planet disk
	            if (canOcclude && !isFrontSide) {
	                double dxs = screenTmp.x - planetScreen.x;
	                double dys = screenTmp.y - planetScreen.y;
	                if (dxs*dxs + dys*dys < planetScreenRadius * planetScreenRadius) continue;
	            }

	            // Shadow
	            float shadow = shadowFactor(sx, sy, sz, wx, wy, wz, px, py, pz, planet.getRadius());

	            // Subpixel particle "kernel" coverage (still drawn as 1 pixel, but alpha scales with area)
	            double rPx = p.sizeUnits * pixelsPerUnit;
	            double coverage = Math.PI * rPx * rPx;

	            // We draw a single pixel, so cap "covered area" to 1 pixel^2 worth of contribution
	            if (coverage > 1.0) coverage = 1.0;
	            if (coverage <= 0.0) continue;

	            // Alpha base from band optical depth (weight), particle coverage, and small jitter.
	            // (Lighting is applied outside the clamp.)
	            float alphaBase = (float)(bandWeight * coverage * p.brightness);

	            // Clamp only on the high end to avoid blowout; do NOT force a minimum (that breaks distance behavior).
	            alphaBase = Math.max(0.0f, Math.min(1.25f, alphaBase));

	            float alpha = alphaBase * shadow * illum * incidence;
	            if (alpha <= 0.0f) continue;


	            // Composite (cached)
	            g2.setComposite(comp(alpha));

	            int xPix = (int) Math.round(screenTmp.x);
	            int yPix = (int) Math.round(screenTmp.y);
	            g2.fillRect(xPix, yPix, 1, 1);
	        }
	    }

	    g2.setComposite(oldComp);
	    g2.setColor(oldColor);
	}

    
    private float shadowFactor(double sx, double sy, double sz,
            double px, double py, double pz,
            double planetX, double planetY, double planetZ,
            double planetR) {
		// Vector from sun to particle
		double dx = px - sx, dy = py - sy, dz = pz - sz;
		double dd = dx*dx + dy*dy + dz*dz;
		if (dd < 1e-12) return 1.0f;
		
		// Closest point on segment [S,P] to planet center C
		double cx = planetX - sx, cy = planetY - sy, cz = planetZ - sz;
		double t = (cx*dx + cy*dy + cz*dz) / dd;
		if (t <= 0.0 || t >= 1.0) return 1.0f; // planet not between sun and particle
		
		double qx = sx + t*dx, qy = sy + t*dy, qz = sz + t*dz;
		double ex = planetX - qx, ey = planetY - qy, ez = planetZ - qz;
		double dist2 = ex*ex + ey*ey + ez*ez;
		
		double r = planetR;
		double soft = ringShadowSoftness * r;
		
		// Hard umbra
		if (soft <= 1e-9) {
			return (dist2 < r*r) ? ringShadowBrightness : 1.0f;
		}
		
		// Soft edge: fade between r and r+soft
		double d = java.lang.Math.sqrt(dist2);
		if (d <= r) return ringShadowBrightness;
		if (d >= r + soft) return 1.0f;
		
		double u = (d - r) / soft; // 0..1
		// smoothstep
		double s = u*u*(3.0 - 2.0*u);
		return (float)(ringShadowBrightness + (1.0 - ringShadowBrightness) * s);
	}
    
    public void addBand(RingBand band) {
        Particle[] ps = generateParticlesForBand(band);

        bands.add(band);
        bandParticles.add(ps);
        bandWeights.add(computeBandWeight(band, ps));
    }
    
    private Particle[] generateParticlesForBand(RingBand band) {
        Particle[] particles = new Particle[band.particleCount];

        java.util.Random rng = new java.util.Random(
                planet.getName().hashCode() ^ (bands.size() * 0x9E3779B9L) ^ band.color.getRGB()
        );

        double radialSpan = band.outerRadius - band.innerRadius;
        double thickness = radialSpan * 0.02; // keep your thickness rule

        // Physical particle radius range (in sim units).
        // Tuned to behave well when rendered as a 1-pixel "kernel" with subpixel coverage.
        double minSize = planet.getRadius() * 0.0000002;
        double maxSize = planet.getRadius() * 0.0000008;

        for (int i = 0; i < band.particleCount; i++) {
            Particle p = new Particle();

            double t = rng.nextDouble();
            p.radius = band.innerRadius + t * radialSpan;

            p.baseAngle = rng.nextDouble() * (2.0 * Math.PI);
            p.height = (rng.nextDouble() - 0.5) * thickness;

            // Particle "kernel" size
            p.sizeUnits = minSize + rng.nextDouble() * (maxSize - minSize);

            // Subtle brightness jitter (don’t let this become a lighting knob)
            p.brightness = 0.9f + 0.2f * rng.nextFloat(); // 0.9..1.1

            particles[i] = p;
        }

        return particles;
    }
    
    private float computeBandWeight(RingBand band, Particle[] ps) {
        // We want: total alpha coverage ≈ opticalDepth * ringBandScreenArea
        // Weight is computed in world-space so it is independent of particleCount.
        double sumSize2 = 0.0;
        for (Particle p : ps) {
            sumSize2 += p.sizeUnits * p.sizeUnits;
        }
        if (sumSize2 <= 1e-18) return 0.0f;

        // Band area in world-space is π(outer^2 - inner^2), and particle area is π(size^2)
        // π cancels, so we can use (outer^2 - inner^2) / sum(size^2)
        double bandAreaNoPi = band.outerRadius * band.outerRadius - band.innerRadius * band.innerRadius;

        double w = band.opticalDepth * (bandAreaNoPi / sumSize2);
        return (float) w;
    }

	public double getAngularSpeed() { return angularSpeed; }

	public Vector3d getNormal() { return normal; }
    
    public List<RingBand> getBands() { return Collections.unmodifiableList(bands); }
}
