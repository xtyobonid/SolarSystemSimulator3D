import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

public abstract class OrbitingBody extends Body {
    protected Body parent;      // star, planet, etc. null for non-orbiting

    protected double a;         // semi-major axis (sim units)
    protected double e;         // eccentricity
    protected double inclRad;   // i
    protected double omegaBigRad;   // Ω (longitude of ascending node)
    protected double omegaSmallRad; // ω (argument of periapsis)
    protected double M0Rad;     // mean anomaly at t=0
    protected double periodSeconds;
    protected double meanMotion;    // n = 2π / period
    
    
    // Cache shaded colors to avoid allocating new Color in hot loops
    private transient int shadeBaseRGB = 0;
    private transient int shadeBaseR = 0, shadeBaseG = 0, shadeBaseB = 0;
    private transient Color[] shadeLUT = new Color[256];


    public OrbitingBody(Body parent) {
        this.parent = parent;
    }
    
    @Override
    protected void drawOverlay(Graphics g, Space s, Frustum frustum,
                               Point2D.Double screenPos, double pixelRadius) {
        if (!s.showLabels) return;
        if (name == null || name.isEmpty()) return;

        // If no parent (e.g. star, or top-level), just use default behavior.
        if (parent == null) {
            super.drawOverlay(g, s, frustum, screenPos, pixelRadius);
            return;
        }

        // Project the parent into screen space
        double parentX = parent.getX();
        double parentY = parent.getY();
        double parentZ = parent.getZ();

        double[] parentCam = frustum.worldToCameraSpaceDirect(parentX, parentY, parentZ);
        Point2D.Double parentScreen = frustum.project3DTo2D(
                parentCam[0], parentCam[1], parentCam[2],
                Space.VIEW_WIDTH, Space.VIEW_HEIGHT
        );

        if (parentScreen != null) {
            double dx = parentScreen.x - screenPos.x;
            double dy = parentScreen.y - screenPos.y;
            double distPx = Math.sqrt(dx*dx + dy*dy);

            // Threshold in pixels where labels would essentially overlap
            // You can tune this; ~15–20px works nicely.
            double thresholdPx = 15.0;

            // Optional: only suppress when we're zoomed out (object is tiny)
            boolean verySmallOnScreen = (pixelRadius < 5.0);

            // Optional: don't hide the label when the parent is very close / we're in “local” mode.
            // For a first pass we can just base it on pixelRadius.
            if (distPx < thresholdPx && verySmallOnScreen) {
                // Too close to parent on screen and tiny → skip this label this frame.
                return;
            }
        }

        // Otherwise, draw label normally
        super.drawOverlay(g, s, frustum, screenPos, pixelRadius);
    }


    @Override
    public void move(long simulationTime) {
        if (parent == null) {
            // Non-orbiting (e.g., star at origin)
            return;
        }

        // simulationTime in ns → seconds
        double tSeconds = simulationTime / 1_000_000_000.0;

        // Optionally wrap to [0, period) for stability
        double tOrbit = tSeconds % periodSeconds;
        if (tOrbit < 0.0) tOrbit += periodSeconds;

        // Mean anomaly M(t) = M0 + n t
        double M = M0Rad + meanMotion * tOrbit;
        // Normalize to [-π, π]
        M = Math.atan2(Math.sin(M), Math.cos(M));

        // Solve Kepler’s equation: M = E - e sin E
        double E = M;
        for (int i = 0; i < 5; i++) {
            double f  = E - e * Math.sin(E) - M;
            double fp = 1.0 - e * Math.cos(E);
            E -= f / fp;
        }

        double cosE = Math.cos(E);
        double sinE = Math.sin(E);

        double r = a * (1.0 - e * cosE);

        // compute true anomaly
        double sqrtOneMinusESq = Math.sqrt(1.0 - e*e);
        double cosNu = (cosE - e) / (1.0 - e * cosE);
        double sinNu = (sqrtOneMinusESq * sinE) / (1.0 - e * cosE);
        double nu    = Math.atan2(sinNu, cosNu);

        // θ = ω + ν
        double theta = omegaSmallRad + nu;

        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        double cosO     = Math.cos(omegaBigRad);
        double sinO     = Math.sin(omegaBigRad);
        double cosI     = Math.cos(inclRad);
        double sinI     = Math.sin(inclRad);

        // Standard orbital-frame → inertial coordinates (astro frame, XY plane)
        double xOrb = r * (cosO * cosTheta - sinO * sinTheta * cosI);
	    double yOrb = r * (sinO * cosTheta + cosO * sinTheta * cosI);
	    double zOrb = r * (sinTheta * sinI);

	    double cx = parent.getX();
	    double cy = parent.getY();
	    double cz = parent.getZ();

	    this.x = cx + xOrb;
	    this.y = cy + zOrb;  // vertical
	    this.z = cz + yOrb;  // in-plane
    }
    
    public Body getParent() { 
    	return parent; 
    }
    
    @Override
    public void draw(Graphics g, Space s, Frustum frustum) {
        if (!(g instanceof Graphics2D)) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;

        double worldX = x;
        double worldY = y;
        double worldZ = z;

        // Project center
        double[] camPos = frustum.worldToCameraSpaceDirect(worldX, worldY, worldZ);
        Point2D.Double projectedPoint = frustum.project3DTo2D(
                camPos[0], camPos[1], camPos[2],
                Space.VIEW_WIDTH, Space.VIEW_HEIGHT
        );
        if (projectedPoint == null) {
            return;
        }

        // Distance to camera
        double dx = worldX - frustum.cameraX;
        double dy = worldY - frustum.cameraY;
        double dz = worldZ - frustum.cameraZ;
        double distanceToCamera = Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        // If camera is inside or extremely near the surface, bail out or draw a minimal marker
        double safety = 1.02; // 2% outside surface
        if (distanceToCamera <= radius * safety) {
            return;
        }

        double pixelRadius = Frustum.computePixelRadiusByProjection(frustum, worldX, worldY, worldZ, radius, projectedPoint);
        
        final int W = Space.VIEW_WIDTH;
        final int H = Space.VIEW_HEIGHT;

        double r  = pixelRadius;

        // Completely off-screen? Skip ALL drawing work.
        if (projectedPoint.x + r < 0 || projectedPoint.x - r > W || projectedPoint.y + r < 0 || projectedPoint.y - r > H) {
            return;
        }
        
        boolean allowOverlays = s.shouldDrawOverlaysFor(this);
        float overlayAlpha = s.computeOverlayAlpha(this, pixelRadius);

        // For tiny bodies, keep your existing fallback logic (no fancy lighting)
        if (pixelRadius < 1.0) {
        	if (!allowOverlays || overlayAlpha <= 0f) return;

            java.awt.Composite oldComp = g2.getComposite();
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, overlayAlpha));
        	
            g2.setColor(color);
            if (drawFallbackIcon(s)) {
                int iconR = getIconRadiusPx();
                int d = iconR;
                g2.drawOval(
                        (int)(projectedPoint.x - iconR / 2.0),
                        (int)(projectedPoint.y - iconR / 2.0),
                        d, d
                );
            } else {
                renderSubpixelPoint(g2, projectedPoint, pixelRadius);
            }
            
            g2.setComposite(oldComp);

            drawOverlay(g2, s, frustum, projectedPoint, pixelRadius);
            return;
        }

        double MAX_PIXEL_RADIUS = 1500.0;
        
        // ----- Find the primary star (Sun) for lighting -----
        Body star = getRootStar();
        if (star == null || star == this) {
            // No star? Just draw flat colored sphere as before.
            g2.setColor(color);
            int d = (int)(2.0 * pixelRadius);
            g2.fillOval(
                    (int)(projectedPoint.x - pixelRadius),
                    (int)(projectedPoint.y - pixelRadius),
                    d, d
            );
            drawOverlay(g2, s, frustum, projectedPoint, pixelRadius);
            return;
        }

        // Direction from this body to the star (world space)
        Vector3d toStarWorld = new Vector3d(
                star.x - worldX,
                star.y - worldY,
                star.z - worldZ
        ).normalize();

        // Light direction in camera space: transform center and a point toward the star
        double sunOffset = radius; // just needs to be non-zero
        double[] camCenter = camPos;
        double[] camSun = frustum.worldToCameraSpaceDirect(
                worldX + toStarWorld.x * sunOffset,
                worldY + toStarWorld.y * sunOffset,
                worldZ + toStarWorld.z * sunOffset
        );

        Vector3d lightDirCam = new Vector3d(
                camSun[0] - camCenter[0],
                camSun[1] - camCenter[1],
                camSun[2] - camCenter[2]
        ).normalize();

        // ----- Shaded disk by scanlines -----
        int rPix = (int)Math.ceil(pixelRadius);
        int cx = (int)Math.round(projectedPoint.x);
        int cy = (int)Math.round(projectedPoint.y);

        final int SEGMENTS = 128; // 24/48/64 - tune. Higher = nicer, slower.
        
        float solar = Lighting.solarIllumAt(x, y, z, star.getX(), star.getY(), star.getZ());

        double fovRad = Math.toRadians(frustum.fov);
        double nearHalfH = frustum.near * java.lang.Math.tan(fovRad * 0.5);
        double nearHalfW = nearHalfH * frustum.aspectRatio;

        // Sphere in camera space:
        double Cx = camPos[0], Cy = camPos[1], Cz = camPos[2];
        double R  = radius;
        double R2 = R * R;
        
        int dyMin = -rPix;
        int dyMax =  rPix;

        // Clamp so yPix = cy + dyPix stays within [0, H-1]
        dyMin = Math.max(dyMin, -cy);
        dyMax = Math.min(dyMax, (H - 1) - cy);

        for (int dyPix = dyMin; dyPix <= dyMax; dyPix++) {
            double sy = dyPix / (double)rPix;
            double xSq = 1.0 - sy*sy;
            if (xSq <= 0) continue;

            int halfWidth = (int)(Math.sqrt(xSq) * rPix);
            int yPix = cy + dyPix;

            int xStart = cx - halfWidth;
            int xEnd   = cx + halfWidth;
            
            // Clip scanline span to screen bounds
            if (xEnd < 0 || xStart > W - 1) continue;   // fully off-screen this row

            int xMin = Math.max(xStart, 0);
            int xMax = Math.min(xEnd, W - 1);
            
            int width = xMax - xMin + 1;
            if (width <= 0) continue;

            // Chunk size in pixels
            int step = Math.max(1, width / SEGMENTS);

            for (int x = xMin; x <= xMax; x += step) {
            	int x2 = Math.min(xMax, x + step - 1);
                int mid = (x + x2) / 2;

                double sx = (mid - cx) / (double)rPix;
                double r2 = sx*sx + sy*sy;
                if (r2 > 1.0) continue;

                double sz = Math.sqrt(1.0 - r2);

                // Convert pixel -> NDC
                double ndcX = (2.0 * mid) / (double)Space.VIEW_WIDTH  - 1.0;
                double ndcY = 1.0 - (2.0 * yPix) / (double)Space.VIEW_HEIGHT;

                // Point on near plane in camera space, then ray dir
                double px = ndcX * nearHalfW;
                double py = ndcY * nearHalfH;
                double pz = -frustum.near;

                // Normalize ray direction d
                double invLen = 1.0 / java.lang.Math.sqrt(px*px + py*py + pz*pz);
                double raydx = px * invLen, raydy = py * invLen, raydz = pz * invLen;

                // Ray-sphere intersection: solve |t d - C|^2 = R^2
                double b = raydx*Cx + raydy*Cy + raydz*Cz;          // d·C
                double c = (Cx*Cx + Cy*Cy + Cz*Cz) - R2;   // |C|^2 - R^2
                double disc = b*b - c;
                if (disc <= 0.0) continue; // no hit

                double tHit = b - java.lang.Math.sqrt(disc);
                if (tHit <= 0.0) continue; // behind camera

                // Hit point P = t d
                double Px = tHit * raydx, Py = tHit * raydy, Pz = tHit * raydz;

                // Normal at hit: n = normalize(P - C)
                double nx = Px - Cx, ny = Py - Cy, nz = Pz - Cz;
                double invN = 1.0 / java.lang.Math.sqrt(nx*nx + ny*ny + nz*nz);
                nx *= invN; ny *= invN; nz *= invN;

                // Lambert
                double lambert = nx*lightDirCam.x + ny*lightDirCam.y + nz*lightDirCam.z;
                lambert = java.lang.Math.max(0.0, lambert);

                double intensity = Lighting.planetIntensity((float)lambert, solar);

                int idx = (int)(intensity * 255.0 + 0.5);
                g2.setColor(getShadedColor(idx));

                g2.drawLine(x, yPix, x2, yPix);
            }
        }


        // Labels, trails, etc.
        drawOverlay(g2, s, frustum, projectedPoint, pixelRadius);
    }
    
    private Color getShadedColor(int intensityIdx) {
        // Re-init cache if the base color changed
        int rgb = this.color.getRGB();
        if (rgb != shadeBaseRGB) {
            shadeBaseRGB = rgb;
            shadeBaseR = color.getRed();
            shadeBaseG = color.getGreen();
            shadeBaseB = color.getBlue();
            // clear LUT
            for (int i = 0; i < 256; i++) shadeLUT[i] = null;
        }

        intensityIdx = Math.max(0, Math.min(255, intensityIdx));
        Color c = shadeLUT[intensityIdx];
        if (c != null) return c;

        // Compute shaded RGB using integer math (idx is 0..255)
        int rr = (shadeBaseR * intensityIdx + 127) / 255;
        int gg = (shadeBaseG * intensityIdx + 127) / 255;
        int bb = (shadeBaseB * intensityIdx + 127) / 255;

        c = new Color(rr, gg, bb);
        shadeLUT[intensityIdx] = c;
        return c;
    }


    public Star getRootStar() {
        Body b = this;
        while (b instanceof OrbitingBody) {
            b = ((OrbitingBody) b).getParent();
        }
        return (b instanceof Star) ? (Star) b : null;
    }

    
    public double getSemiMajorAxis() { return a; }

}
