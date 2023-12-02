import java.awt.*;
import java.awt.geom.Point2D;
import java.lang.Math;

public class Star extends Body {
	private double x;
	private double y;
	private double radius;
	private String type;
	private Color color = new Color(253, 216, 53);
	
	private static final int ICON_RADIUS = 12;
	
	public Star (double wWidth, double wHeight, double radius2) {
		x = wWidth / 2;
		y = wHeight / 2;
		radius = radius2;
		type = "star";
	}
	
	public void draw(Graphics g, Space s, Frustrum frustrum) {
		double z=0;
	    // Transform the planet's position to camera space
	    double[] cameraSpacePosition = frustrum.worldToCameraSpace(x, y, z, frustrum.computeViewMatrix());

	    // Project the 3D point to 2D screen space
	    Point2D.Double projectedPoint = frustrum.project3DTo2D(cameraSpacePosition[0], cameraSpacePosition[1], cameraSpacePosition[2], s.VIEW_WIDTH, s.VIEW_HEIGHT);
	    
	    // Only draw the planet if it is in front of the camera
	    if (projectedPoint != null) {
	        // Calculate the projected radius based on the distance to the camera and the planet's actual radius
	        double distanceToCamera = Math.sqrt(Math.pow(x - frustrum.cameraX, 2) + Math.pow(y - frustrum.cameraY, 2) + Math.pow(z - frustrum.cameraZ, 2));
	        double angle = Math.atan(radius / distanceToCamera);
	        double projectedRadius =(Math.tan(angle) * frustrum.near);
	        
	        double circleSize = ((projectedRadius / (0.5 * frustrum.near * (double) Math.tan(Math.toRadians(frustrum.fov / 2.0)))) * s.VIEW_HEIGHT);
	        
//	        System.out.println("Sun position: " + projectedPoint.x + ", " + projectedPoint.y);
//	        System.out.println("Sun Radius: " + projectedRadius);
//	        System.out.println("Sun distance to camera: " + distanceToCamera);
//	        System.out.println("Sun angular size: " + angle);
//	        System.out.println("Sun display size: " + circleSize);

	        g.setColor(color);
	        // Draw the planet as a circle on the screen
	        g.fillOval((int) (projectedPoint.x - circleSize/2), (int) (projectedPoint.y - circleSize/2), (int) (circleSize), (int) (circleSize));
	    }
	    
	    System.out.println();
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