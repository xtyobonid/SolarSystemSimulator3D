import java.awt.*;
import java.util.Scanner;

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
	
	public void draw (Graphics window, Space s, Frustrum frustrum) {
		if (frustrum.sphereInFrustum(new Vector3d(x, y, 0), radius)) {
			window.setColor(color);
			
			Vector3d viewCoords = frustrum.getIntersectionWithViewPlane(new Vector3d(x, y, 0), s.VIEW_WIDTH, radius);
			double realX = viewCoords.x;
			double realY = viewCoords.y;
			double realRadius = viewCoords.z;
			
			//System.out.println(realX + " " + realY);
			
			
			if (realRadius >= 1) { //actually draw it
				window.fillOval((int) realX - (int) realRadius, (int) realY - (int) realRadius, (int) realRadius * 2, (int) realRadius * 2);
			} else { //draw icon
				window.drawOval((int) realX - ICON_RADIUS, (int) realY - ICON_RADIUS, (int) ICON_RADIUS * 2, (int) ICON_RADIUS * 2);
			}
			//window.drawOval((int) star.getX() - (int) orbitD, (int) star.getY() - (int) orbitD, (int) orbitD * 2, (int) orbitD * 2);
		}
	}
	
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