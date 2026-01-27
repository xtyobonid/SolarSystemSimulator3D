import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.lang.Math;
import java.awt.geom.Point2D;

public abstract class Body {
    protected double x;
    protected double y;
    protected double z;

    protected double radius;   // in sim units (same units as positions)
    protected Color color;

    protected String name;
    protected String type;     // "star", "planet", "moon", "asteroid", ...

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public double getRadius() { return radius; }
    public String getName()   { return name; }
    public String getType()   { return type; }

    /** How big the fallback icon is, in pixels, when planet is sub-pixel. */
    protected abstract int getIconRadiusPx();

    /** Allow subclasses to hide icons completely if you ever want that. */
    protected boolean drawFallbackIcon(Space space) { return space.showIcons; }

    /** Subclasses will override if they want special label/hud, etc. */
    protected void drawOverlay(Graphics g, Space s, Frustum frustum,
                               Point2D.Double screenPos, double pixelRadius) {
    	if (!s.showLabels) return;
        if (name == null || name.isEmpty()) return;
        if (!s.shouldDrawOverlaysFor(this)) return;

        float a = s.computeOverlayAlpha(this, pixelRadius);
        if (a <= 0f) return;

        if (!(g instanceof Graphics2D g2)) return;

        java.awt.Composite oldComp = g2.getComposite();
        java.awt.Color oldColor = g2.getColor();
        
        g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));

        // Position label just above the body
        int xScreen = (int) Math.round(screenPos.x);
        int yScreen = (int) Math.round(screenPos.y - pixelRadius - 4.0);

        // Simple outline for readability
        g.setColor(Color.BLACK);
        g.drawString(name, xScreen + 1, yScreen + 1);

        g.setColor(Color.WHITE);
        g.drawString(name, xScreen, yScreen);

        g2.setComposite(oldComp);
        g2.setColor(oldColor);
    }

    /** Base move does nothing; orbiting bodies will override. */
    public void move(long simulationTime) {}

    /** Common projection + circle drawing for all spherical bodies. */
    public void draw(Graphics g, Space s, Frustum frustum) {
        double worldX = x;
        double worldY = y;
        double worldZ = z;

        double[] cameraSpacePosition = frustum.worldToCameraSpaceDirect(worldX, worldY, worldZ);

        Point2D.Double projectedPoint = frustum.project3DTo2D(cameraSpacePosition[0], cameraSpacePosition[1], cameraSpacePosition[2], Space.VIEW_WIDTH, Space.VIEW_HEIGHT);

        if (projectedPoint == null) {
            return;
        }

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
        
        boolean allowOverlays = s.shouldDrawOverlaysFor(this);
        float overlayAlpha = s.computeOverlayAlpha(this, pixelRadius);

        if (pixelRadius < 1 && (!allowOverlays || overlayAlpha <= 0))
    		return;
        
        g.setColor(color);
      
        if (pixelRadius >= 1.0) {
            int d = (int)(2.0 * pixelRadius);
            g.fillOval((int)(projectedPoint.x - pixelRadius),
                       (int)(projectedPoint.y - pixelRadius),
                       d, d);
        } else if (drawFallbackIcon(s)) {
            int iconR = getIconRadiusPx();
            int d = iconR;
            g.drawOval((int)(projectedPoint.x - iconR/2.0),
                       (int)(projectedPoint.y - iconR/2.0),
                       d, d);
        } else {
        	// pixelRadius < 1.0 and no fallback icon → do subpixel rendering
            renderSubpixelPoint(g, projectedPoint, pixelRadius);
        }

        drawOverlay(g, s, frustum, projectedPoint, pixelRadius);
    }
    
    protected void renderSubpixelPoint(Graphics g, Point2D.Double screenPos, double pixelRadius) {
        if (!(g instanceof Graphics2D g2)) {
            // Fallback: just draw a 1x1 dot
            int x = (int) Math.round(screenPos.x);
            int y = (int) Math.round(screenPos.y);
            g.fillRect(x, y, 1, 1);
            return;
        }

        // Fractional pixel coverage ~ area of the projected disk
        double coverage = Math.PI * pixelRadius * pixelRadius; // 0..~1

        // Clamp and slightly bias so it never fully disappears
        double alpha = Math.min(1.0, coverage);
        double minAlpha = 0.1; // you can tweak this
        alpha = minAlpha + (1.0 - minAlpha) * alpha; // blend between [minAlpha, 1]

        // Darken color a bit for very small radii
        double brightness = Math.min(1.0, coverage);
        int r = (int) (color.getRed()   * brightness);
        int gCol = (int) (color.getGreen() * brightness);
        int b = (int) (color.getBlue()  * brightness);
        Color dimmed = new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, gCol)),
                Math.max(0, Math.min(255, b))
        );

        // Save old state
        java.awt.Composite oldComp = g2.getComposite();
        Color oldColor = g2.getColor();

        g2.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER,
                (float) alpha
        ));
        g2.setColor(dimmed);

        int xPix = (int) Math.round(screenPos.x);
        int yPix = (int) Math.round(screenPos.y);

        // you can experiment with 1x1 or 2x2 here; 1x1 is "sharper", 2x2 is "softer"
        g2.fillRect(xPix, yPix, 1, 1);

        // restore
        g2.setComposite(oldComp);
        g2.setColor(oldColor);
    }
}
