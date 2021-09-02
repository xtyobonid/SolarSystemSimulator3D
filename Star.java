import java.awt.*;

public class Star extends Body {
	private double x;
	private double y;
	private double radius;
	private String type;
	
	private static final int ICON_RADIUS = 12;
	
	public Star (double wWidth, double wHeight, double radius2) {
		x = wWidth / 2;
		y = wHeight / 2;
		radius = radius2;
		type = "star";
	}
	
	public void draw (Graphics window, Space s, Frustrum frustrum) {
		if (frustrum.sphereInFrustum(new Vector3d(x, y, 0), radius)) {
			window.setColor(new Color(239, 142, 56));
			
			Vector3d viewCoords = frustrum.getIntersectionWithViewPlane(new Vector3d(x, y, 0), s.VIEW_WIDTH, radius);
			double realX = viewCoords.x;
			double realY = viewCoords.y;
			double realRadius = viewCoords.z;
			//System.out.println(realX + " " + realY + " " + realRadius);
			
			if (realRadius > 1) { //actually draw it
				window.fillOval((int) realX - (int) realRadius, (int) realY - (int) realRadius, (int) realRadius * 2, (int) realRadius * 2);
			} else { //draw icon
				window.drawOval((int) realX - ICON_RADIUS, (int) realY - ICON_RADIUS, (int) ICON_RADIUS * 2, (int) ICON_RADIUS * 2);
			}
		}
	}
	
	public double getX () {
		return x;
	}
	
	public double getY () {
		return y;
	}
	
	public double getRadius() {
		return radius;
	}
	
	public String getType() {
		return type;
	}
	
}