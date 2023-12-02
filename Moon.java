import java.awt.*;
import java.awt.geom.Point2D;
import java.lang.Math;
import java.util.Scanner;

public class Moon extends Body {
	private double x;
	private double y;
	private double radius;
	private Color color;
	private String name;
	private double orbitD;
	private double orbitS;
	private Planet planet;
	private double orbitA;
	private double startAngle;
	private double displaySpeed;
	private String type;
	
	private static final int ICON_RADIUS = 2;
	
	public Moon (String serialization, Planet planet2, double displaySpeed2) {
		planet = planet2;
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
		type = "moon";
	}

	public Moon (double radius2, double orbitD2, Planet planet2, String name2, Color color2, double orbitS2) {
		x = 0;
		y = 0;
		displaySpeed = 1;
		planet = planet2;
		radius = radius2;
		orbitD = orbitD2;
		
		double nanoseconds = orbitS2 * 8.64 * java.lang.Math.pow(10, 13);
		orbitS = (-360)/nanoseconds;
		
		orbitA = 360;
		name = name2;
		color = color2;
		type = "moon";
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

	        g.setColor(color);
	        // Draw the planet as a circle on the screen
	        g.fillOval((int) (projectedPoint.x - circleSize/2), (int) (projectedPoint.y - circleSize/2), (int) (circleSize), (int) (circleSize));
	    }
	    
	    System.out.println();
	}
	public void move (long simulationTime) {
		orbitA = orbitS * simulationTime + 360 + startAngle;

		x = (orbitD * Math.cos((double) Math.toRadians(orbitA))) + planet.getX();
		y = (orbitD * Math.sin((double) Math.toRadians(orbitA))) + planet.getY();
	}
	
	public void setDisplaySpeed(double ds) {
		displaySpeed = ds;
	}
	
	public String getPlanetName() {
		return planet.getName();
	}
	
	public String getName() {
		return name;
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
	
	public Planet getPlanet() {
		return planet;
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
		ret += planet.getName();
		
		return ret;
	}
	
	public String getType() {
		return type;
	}
 }
