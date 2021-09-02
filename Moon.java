import java.awt.*;
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

	public void draw (Graphics window, Space s, Frustrum frustrum) {
		if (frustrum.sphereInFrustum(new Vector3d(x, y, 0), radius)) {
			window.setColor(color);
			
			Vector3d viewCoords = frustrum.getIntersectionWithViewPlane(new Vector3d(x, y, 0), s.VIEW_WIDTH, radius);
			double realX = viewCoords.x;
			double realY = viewCoords.y;
			double realRadius = viewCoords.z;
			
			if (realRadius > 1) { //actually draw it
				window.fillOval((int) realX - (int) realRadius, (int) realY - (int) realRadius, (int) realRadius * 2, (int) realRadius * 2);
			} else { //draw icon
				window.drawOval((int) realX - ICON_RADIUS, (int) realY - ICON_RADIUS, (int) ICON_RADIUS * 2, (int) ICON_RADIUS * 2);
			}
			
			//window.drawOval((int) planet.getX() - (int) orbitD, (int) planet.getY() - (int) orbitD, (int) orbitD * 2, (int) orbitD * 2);
		}
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
