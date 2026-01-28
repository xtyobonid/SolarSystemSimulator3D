import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * The current AWT/Canvas renderer extracted from Space.paint + helpers.
 *
 * Goal: keep Space focused on simulation/time/input, and keep drawing code here.
 * This makes it much easier to replace this renderer with an LWJGL/OpenGL renderer.
 */
public final class SoftwareRenderer implements Renderer {

    private BufferedImage back;

    // Reuse buffers to avoid GC (moved from Space)
    private final double[] orbitCamTmp = new double[4];
    private final Point2D.Double orbitScreenTmp = new Point2D.Double();
    private final Point2D.Double orbitCenterScreenTmp = new Point2D.Double();

    @Override
    public void render(Graphics window, Space space) {
        Graphics2D tdg = (Graphics2D) window;

        // Back buffer
        int w = Space.VIEW_WIDTH;
        int h = Space.VIEW_HEIGHT;
        if (back == null || back.getWidth() != w || back.getHeight() != h) {
            back = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D gtb = back.createGraphics();

        // Clear
        gtb.setColor(Color.BLACK);
        gtb.fillRect(0, 0, w, h);

        // Orbits (behind bodies)
        drawOrbits(gtb, space);

        // Starfield
        if (space.showStars && space.getStarfield() != null) {
            space.getStarfield().draw(gtb, Space.frustum, w, h);
        }

        // Build draw list (same ordering and behavior as before)
        ArrayList<Body> drawList = new ArrayList<>();

        for (Moon m : space.getMoons()) drawList.add(m);
        for (Planet p : space.getPlanets()) drawList.add(p);
        for (Asteroid a : space.getAsteroids()) drawList.add(a);
        drawList.add(space.getStar());

        drawList = sortDrawList(drawList);

        // Draw bodies
        for (Body b : drawList) {
            b.draw(gtb, space, Space.frustum);
        }

        // HUD
        drawHud(gtb, space);

        gtb.dispose();
        tdg.drawImage(back, null, 0, 0);
    }

    private void drawHud(Graphics2D gtb, Space space) {
        gtb.setColor(Color.WHITE);
        int hudY = 20;

        double baseSpeed = space.getBaseSpeed();
        int speedLevel = space.getSpeedLevel();

        double speedKmPerSec = baseSpeed * java.lang.Math.pow(2.0, speedLevel) * Space.SCALE_KM_PER_UNIT;
        double simSecondsPerSecond = space.getDisplaySpeed();
        String simSpeedStr = formatSimSpeed(simSecondsPerSecond);

        gtb.drawString("Sim Speed: " + simSpeedStr, 10, hudY);
        hudY += 15;

        gtb.drawString(
                String.format("Speed: base * 2^%d  (%.3g km/s)", speedLevel, speedKmPerSec),
                10, hudY
        );
        hudY += 15;

        gtb.drawString(
                "W/S: forward/back  A/D: strafe  Space/Ctrl: up/down  Q/E: speed -/+  L: toggle labels  ?:  Show nearest body info",
                10, hudY
        );
        hudY += 45;

        // --- Nearest body info HUD ---
        Body lastInfoBody = space.getLastInfoBody();
        if (lastInfoBody != null && space.getLastCurrentTime() < space.getInfoHudUntilNanos()) {
            double bx = lastInfoBody.getX();
            double by = lastInfoBody.getY();
            double bz = lastInfoBody.getZ();

            double cx = Space.frustum.cameraX;
            double cy = Space.frustum.cameraY;
            double cz = Space.frustum.cameraZ;

            double dx = bx - cx;
            double dy = by - cy;
            double dz = bz - cz;

            double distUnits = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            double distKm = distUnits * Space.SCALE_KM_PER_UNIT;

            String type = lastInfoBody.getType();
            String name = lastInfoBody.getName();
            double radiusUnits = lastInfoBody.getRadius();
            double radiusKm = radiusUnits * Space.SCALE_KM_PER_UNIT;

            gtb.setColor(Color.WHITE);
            gtb.drawString(String.format("Nearest: %s (%s)", name, type), 10, hudY);
            hudY += 15;

            gtb.drawString(
                    String.format("Distance from camera: %.3g units  (~%.3g km)", distUnits, distKm),
                    10, hudY
            );
            hudY += 15;

            gtb.drawString(
                    String.format("Radius: %.3g units  (~%.3g km)", radiusUnits, radiusKm),
                    10, hudY
            );
            hudY += 15;

            // Extra details if this is an orbiting body
            if (lastInfoBody instanceof OrbitingBody ob) {
                Body parent = ob.getParent();

                double e = ob.e;
                double periodDays = ob.periodSeconds / 86400.0;
                double periodYears = periodDays / 365.25;

                String parentName = (parent != null ? parent.getName() : "None");

                gtb.drawString(String.format("Orbits: %s", parentName), 10, hudY);
                hudY += 15;

                gtb.drawString(String.format("Eccentricity: %.4f", e), 10, hudY);
                hudY += 15;

                gtb.drawString(
                        String.format("Orbital period: %.3g days  (%.3g years)", periodDays, periodYears),
                        10, hudY
                );
                hudY += 15;

                // Distance to parent right now
                if (parent != null) {
                    double px = parent.getX();
                    double py = parent.getY();
                    double pz = parent.getZ();

                    double pdx = bx - px;
                    double pdy = by - py;
                    double pdz = bz - pz;

                    double parentDistUnits = java.lang.Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);
                    double parentDistKm = parentDistUnits * Space.SCALE_KM_PER_UNIT;

                    gtb.drawString(
                            String.format(
                                    "Current distance to parent: %.3g units  (~%.3g km)",
                                    parentDistUnits, parentDistKm
                            ),
                            10, hudY
                    );
                    hudY += 15;
                }
            }
        }
    }

    private void drawOrbits(Graphics2D g2, Space space) {
        if (!space.showPlanetOrbits && !space.showMoonOrbits && !space.showAsteroidOrbits) return;

        Composite oldComp = g2.getComposite();
        Stroke oldStroke = g2.getStroke();

        g2.setStroke(new BasicStroke(1f));

        if (space.showPlanetOrbits) {
            for (Planet p : space.getPlanets()) {
                if (p == null) continue;
                if (!space.shouldDrawOverlaysFor(p)) continue;
                drawOrbitPathFor((OrbitingBody) p, g2, space);
            }
        }

        if (space.showMoonOrbits) {
            for (Moon m : space.getMoons()) {
                if (m == null) continue;
                if (!space.shouldDrawOverlaysFor(m)) continue;
                drawOrbitPathFor((OrbitingBody) m, g2, space);
            }
        }

        if (space.showAsteroidOrbits) {
            for (Asteroid a : space.getAsteroids()) {
                if (a == null) continue;
                if (!space.shouldDrawOverlaysFor(a)) continue;
                drawOrbitPathFor((OrbitingBody) a, g2, space);
            }
        }

        g2.setComposite(oldComp);
        g2.setStroke(oldStroke);
    }

    private void drawOrbitPathFor(OrbitingBody ob, Graphics2D g2, Space space) {
        Body parent = ob.getParent();
        if (parent == null) return;

        double a = ob.getSemiMajorAxis();
        double e = ob.e;

        double apo = a * (1.0 + e);
        if (apo <= 0) return;

        // Project orbit center (parent) to estimate on-screen size
        Space.frustum.worldToCameraSpaceDirect(parent.getX(), parent.getY(), parent.getZ(), orbitCamTmp);
        if (!Space.frustum.project3DTo2D(
                orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
                Space.VIEW_WIDTH, Space.VIEW_HEIGHT, orbitCenterScreenTmp
        )) {
            return;
        }

        double pxPerUnit = estimatePixelsPerUnitAt(parent.getX(), parent.getY(), parent.getZ());
        if (pxPerUnit <= 0) return;

        double orbitPx = apo * pxPerUnit;
        if (orbitPx < space.orbitMinVisiblePx) return;

        // Fade
        double t = orbitPx / space.orbitFadeRefPx;
        t = java.lang.Math.max(0.0, java.lang.Math.min(1.0, t));
        double shaped = java.lang.Math.pow(t, space.orbitFadeGamma);
        float alpha = (float) (space.orbitBaseAlpha * shaped);
        if (alpha <= 0.001f) return;

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setColor(orbitTint(ob.color, 0.25f, 70));

        // Adaptive segment count
        int seg = (int) java.lang.Math.round(
                space.orbitSegmentsMin +
                        (space.orbitSegmentsMax - space.orbitSegmentsMin) *
                                java.lang.Math.min(1.0, orbitPx / 400.0)
        );
        seg = java.lang.Math.max(space.orbitSegmentsMin, java.lang.Math.min(space.orbitSegmentsMax, seg));

        double cosO = java.lang.Math.cos(ob.omegaBigRad);
        double sinO = java.lang.Math.sin(ob.omegaBigRad);
        double cosI = java.lang.Math.cos(ob.inclRad);
        double sinI = java.lang.Math.sin(ob.inclRad);
        double cosw = java.lang.Math.cos(ob.omegaSmallRad);
        double sinw = java.lang.Math.sin(ob.omegaSmallRad);

        boolean hasPrev = false;
        int prevX = 0, prevY = 0;

        for (int i = 0; i <= seg; i++) {
            double nu = (2.0 * java.lang.Math.PI) * (i / (double) seg);
            double r = (a * (1.0 - e * e)) / (1.0 + e * java.lang.Math.cos(nu));

            double xP = r * java.lang.Math.cos(nu);
            double yP = r * java.lang.Math.sin(nu);

            // Rotate by ω
            double x1 = xP * cosw - yP * sinw;
            double y1 = xP * sinw + yP * cosw;

            // Rotate by i
            double x2 = x1;
            double y2 = y1 * cosI;
            double z2 = y1 * sinI;

            // Rotate by Ω
            double xEc = x2 * cosO - y2 * sinO;
            double yEc = x2 * sinO + y2 * cosO;
            double zEc = z2;

            // ECLIPTIC -> ENGINE remap (matching your existing convention)
            double xEng = xEc;
            double yEng = zEc;
            double zEng = yEc;

            // World translate
            double wx = parent.getX() + xEng;
            double wy = parent.getY() + yEng;
            double wz = parent.getZ() + zEng;

            Space.frustum.worldToCameraSpaceDirect(wx, wy, wz, orbitCamTmp);
            if (!Space.frustum.project3DTo2D(
                    orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
                    Space.VIEW_WIDTH, Space.VIEW_HEIGHT, orbitScreenTmp
            )) {
                hasPrev = false;
                continue;
            }

            int x = (int) java.lang.Math.round(orbitScreenTmp.x);
            int y = (int) java.lang.Math.round(orbitScreenTmp.y);

            if (hasPrev) g2.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
            hasPrev = true;
        }
    }

    private double estimatePixelsPerUnitAt(double wx, double wy, double wz) {
        // project center
        Space.frustum.worldToCameraSpaceDirect(wx, wy, wz, orbitCamTmp);
        if (!Space.frustum.project3DTo2D(
                orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
                Space.VIEW_WIDTH, Space.VIEW_HEIGHT, orbitCenterScreenTmp
        )) {
            return 0.0;
        }

        // project point offset by camera-right
        double[] f = new double[3];
        double[] r = new double[3];
        double[] u = new double[3];
        Frustum.computeCameraBasis(Space.yaw, Space.pitch, f, r, u);

        double wx2 = wx + r[0];
        double wy2 = wy + r[1];
        double wz2 = wz + r[2];

        Space.frustum.worldToCameraSpaceDirect(wx2, wy2, wz2, orbitCamTmp);
        if (!Space.frustum.project3DTo2D(
                orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
                Space.VIEW_WIDTH, Space.VIEW_HEIGHT, orbitScreenTmp
        )) {
            return 0.0;
        }

        double dx = orbitScreenTmp.x - orbitCenterScreenTmp.x;
        double dy = orbitScreenTmp.y - orbitCenterScreenTmp.y;
        return java.lang.Math.sqrt(dx * dx + dy * dy);
    }

    private static Color orbitTint(Color bodyColor, float tintAmount, int baseGray) {
        int r = (int) (baseGray + tintAmount * (bodyColor.getRed() - baseGray));
        int g = (int) (baseGray + tintAmount * (bodyColor.getGreen() - baseGray));
        int b = (int) (baseGray + tintAmount * (bodyColor.getBlue() - baseGray));
        r = java.lang.Math.max(0, java.lang.Math.min(255, r));
        g = java.lang.Math.max(0, java.lang.Math.min(255, g));
        b = java.lang.Math.max(0, java.lang.Math.min(255, b));
        return new Color(r, g, b);
    }

    private static String formatSimSpeed(double simSecondsPerSecond) {
        double sec = simSecondsPerSecond;

        if (sec < 120) {
            return String.format("%.2f seconds/sec", sec);
        }

        double minutes = sec / 60.0;
        if (minutes < 120) {
            return String.format("%.2f minutes/sec", minutes);
        }

        double hours = minutes / 60.0;
        if (hours < 48) {
            return String.format("%.2f hours/sec", hours);
        }

        double days = hours / 24.0;
        if (days < 365) {
            return String.format("%.2f days/sec", days);
        }

        double years = days / 365.25;
        return String.format("%.2f years/sec", years);
    }

    private static ArrayList<Body> sortDrawList(ArrayList<Body> drawList) {
        int n = drawList.size();
        for (int i = 1; i < n; ++i) {
            Body keyBody = drawList.get(i);

            double bx = keyBody.getX();
            double by = keyBody.getY();
            double bz = keyBody.getZ();

            double keyDist = new Vector3d(bx, by, bz)
                    .distance(new Vector3d(Space.frustum.cameraX, Space.frustum.cameraY, Space.frustum.cameraZ));

            int j = i - 1;
            while (j >= 0) {
                Body jb = drawList.get(j);
                double jbx = jb.getX();
                double jby = jb.getY();
                double jbz = jb.getZ();

                double jbDist = new Vector3d(jbx, jby, jbz)
                        .distance(new Vector3d(Space.frustum.cameraX, Space.frustum.cameraY, Space.frustum.cameraZ));

                if (jbDist >= keyDist) break;

                drawList.set(j + 1, jb);
                j--;
            }
            drawList.set(j + 1, keyBody);
        }
        return drawList;
    }
}
