import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Scanner;
import java.lang.Math;

public class Planet extends Body {
	private double x;
	private double y;
	private double radius;
	private Color color;
	private double orbitD;
	private double orbitA;
	private double startAngle;
	private double orbitS;
	private String name;
	private Star star;
	private double displaySpeed;
	private String type;
	
	private static final int ICON_RADIUS = 5;
	
	//load planet
	public Planet (String serialization, Star star2, double displaySpeed2) {
		star = star2;
		displaySpeed = displaySpeed2;
		
		Scanner load = new Scanner(serialization);
		x = load.nextDouble();
		y = load.nextDouble();
		radius = load.nextDouble();
		int red = load.nextInt();
		int green = load.nextInt();
		int blue = load.nextInt();
		color = new Color(red, green, blue);
		orbitD = load.nextDouble();
		startAngle = load.nextDouble();
		orbitS = load.nextDouble();
		name = load.next();
		type = "planet";
		
	}
	
	public Planet (String name2, double radius2, double startAngle2, double orbitD2, Star star2, double orbitS2, Color color2) {
		x = 0;
		y = 0;
		displaySpeed = 1;
		startAngle = startAngle2;
		name = name2;
		star = star2;
		radius = radius2;
		orbitD = orbitD2;
		
		//calculate orbital speed, given days
		//angle = 360 - ()(nanoseconds)
		//y = orbitS * X + 360
		double nanoseconds = orbitS2 * 8.64 * java.lang.Math.pow(10, 13);
		
		orbitS = (-360)/nanoseconds;
		orbitA = 360;
		color = color2;
		type = "planet";
	}
	
	public Planet () {
		
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

	        if (name.equals("Earth")) {
	        	System.out.println(name + " position: " + projectedPoint.x + ", " + projectedPoint.y);
	        	System.out.println(orbitD + " " + (x - star.getX()) + " " + (y - star.getY()) + " " + circleSize);
	        	System.out.println((float) Math.sqrt(Math.pow(x - frustrum.cameraX, 2) + Math.pow(y - frustrum.cameraY, 2) + Math.pow(z - frustrum.cameraZ, 2)));
	        }
	        
	        g.setColor(color);
	        // Draw the planet as a circle on the screen
	        g.fillOval((int) (projectedPoint.x - circleSize/2), (int) (projectedPoint.y - circleSize/2), (int) (circleSize), (int) (circleSize));
	        
	        if (circleSize < 1) {
	        	g.drawOval((int) (projectedPoint.x - circleSize/2), (int) (projectedPoint.y - circleSize/2), (int) (ICON_RADIUS), (int) (ICON_RADIUS));
	        }
	    }
	    
	    System.out.println();
	}
	
//	public void draw (Graphics window, double[] viewMatrix, Space s, Frustrum frustrum) {
//		double z = 0;
//		Point screenPos = ProjectionUtil.project3DTo2D(x, y, z, viewMatrix, frustrum, s.VIEW_WIDTH, s.VIEW_HEIGHT);
//		
//		// Calculate the projected radius based on the distance to the camera and the planet's actual radius
//        float distanceToCamera = (float) Math.sqrt(Math.pow(x - frustrum.cameraX, 2) + Math.pow(y - frustrum.cameraY, 2) + Math.pow(z - frustrum.cameraZ, 2));
//        float angle = (float) Math.atan(radius / distanceToCamera);
//        float projectedRadius = (float) (Math.tan(angle) * frustrum.near);
//
//        // Calculate the size of the circle on the screen based on the projected radius
//        int circleSize = (int) ((projectedRadius / (0.5f * frustrum.near * (float) Math.tan(Math.toRadians(frustrum.fov / 2.0)))) * s.VIEW_HEIGHT);
//
//        if (name.equals("Earth")) {
//        	System.out.println(name + " position: " + screenPos.x + ", " + screenPos.y);
//        	System.out.println(orbitD + " " + (x - star.getX()) + " " + (y - star.getY()) + " " + circleSize);
//        	System.out.println((float) Math.sqrt(Math.pow(x - frustrum.cameraX, 2) + Math.pow(y - frustrum.cameraY, 2) + Math.pow(z - frustrum.cameraZ, 2)));
//        }
//        
//        window.setColor(color);
//        // Draw the planet as a circle
//        window.fillOval(screenPos.x - circleSize / 2, screenPos.y - circleSize / 2, circleSize, circleSize);
//        
//        if (circleSize < 1) {
//        	//window.drawOval(screenPos.x - ICON_RADIUS / 2, screenPos.y - ICON_RADIUS / 2, ICON_RADIUS, ICON_RADIUS);
//        }
//	}
	
	public void move (long simulationTime) {
		orbitA = orbitS * simulationTime + 360 + startAngle;

		x = (orbitD * Math.cos((double) Math.toRadians(orbitA))) + star.getX();
		y = (orbitD * Math.sin((double) Math.toRadians(orbitA))) + star.getY();
	}
	
	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public double getRadius() {
		return radius;
	}
	
	public double getOrbitDistance() {
		return orbitD;
	}
	
	public double getOrbitSpeed() {
		return orbitS;
	}
	
	public String getName() {
		return name;
	}
	
	public void setDisplaySpeed(double ds) {
		displaySpeed = ds;
	}
	
	public String save() {
		String ret = "";
		
		ret += x + " ";
		ret += y + " ";
		ret += radius + " ";
		ret += color.getRed() + " ";
		ret += color.getGreen() + " ";
		ret += color.getBlue() + " ";
		ret += orbitD + " ";
		ret += startAngle + " ";
		ret += orbitS + " ";
		ret += name + " ";
		
		return ret;
	}
	
	public String getType() {
		return type;
	}
}