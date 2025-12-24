import java.awt.*;
import java.awt.geom.Point2D;
import java.lang.Math;
import java.util.Scanner;

public class Moon extends OrbitingBody {
	private static final int ICON_RADIUS = 3;
	
	private Planet planet;
	
    public Moon(String serialization, Planet parentPlanet, double displaySpeed) {
        super(parentPlanet);
        this.planet = parentPlanet;

        Scanner load = new Scanner(serialization);

        this.x = load.nextDouble();
        this.y = load.nextDouble();
        this.z = load.nextDouble();

        this.radius = load.nextDouble();
        int red   = load.nextInt();
        int green = load.nextInt();
        int blue  = load.nextInt();
        this.color = new Color(red, green, blue);

        this.a = load.nextDouble();
        this.e = load.nextDouble();
        double iDeg     = load.nextDouble();
        double omegaDeg = load.nextDouble();
        double wDeg     = load.nextDouble();
        double M0Deg    = load.nextDouble();
        double periodDays = load.nextDouble();

        this.name = load.next();
        this.type = "moon";

        load.close();

        this.inclRad       = Math.toRadians(iDeg);
        this.omegaBigRad   = Math.toRadians(omegaDeg);
        this.omegaSmallRad = Math.toRadians(wDeg);
        this.M0Rad         = Math.toRadians(M0Deg);

        this.periodSeconds = periodDays * 86400.0;
        this.meanMotion    = (2.0 * Math.PI) / this.periodSeconds;
    }

//	public Moon (double radius2, double orbitD2, Planet planet2, String name2, Color color2, double orbitS2) {
//		x = 0;
//		y = 0;
//		z = 0; 
//		
//		displaySpeed = 1;
//		planet = planet2;
//		radius = radius2;
//		orbitD = orbitD2;
//		
//		double nanoseconds = orbitS2 * 8.64 * java.lang.Math.pow(10, 13);
//		orbitS = (-360)/nanoseconds;
//		
//		orbitA = 360;
//		name = name2;
//		color = color2;
//		type = "moon";
//	}
	
	public String getPlanetName() {
		return planet.getName();
	}
	
	public Planet getPlanet() {
		return planet;
	}
	
//	public String save() { 
//		String ret = "";
//		
//		ret += x + " ";
//		ret += y + " ";
//		ret += radius + " ";
//		ret += color.getRed() + " ";
//		ret += color.getGreen() + " ";
//		ret += color.getBlue() + " ";
//		ret += orbitD + " ";
//		ret += startAngle + " ";
//		ret += orbitS + " ";
//		ret += name + " ";
//		ret += planet.getName();
//		
//		return ret;
//	}
	
	protected int getIconRadiusPx() {
        return ICON_RADIUS;
    }
 }
