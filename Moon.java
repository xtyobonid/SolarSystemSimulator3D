import java.awt.*;
import java.lang.Math;
import java.util.Scanner;

public class Moon extends OrbitingBody {
	private static final int ICON_RADIUS = 3;
	
	private final Planet planet;
	
    public Moon(String serialization, Planet parentPlanet) {
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
	
	public String getPlanetName() {
		return planet.getName();
	}
	
	public Planet getPlanet() {
		return planet;
	}

    public String save() {
        double periodDays = periodSeconds / 86400.0;

        return x + " " + y + " " + z + " " +
                radius + " " +
                color.getRed() + " " + color.getGreen() + " " + color.getBlue() + " " +
                a + " " + e + " " +
                Math.toDegrees(inclRad) + " " +
                Math.toDegrees(omegaBigRad) + " " +
                Math.toDegrees(omegaSmallRad) + " " +
                Math.toDegrees(M0Rad) + " " +
                periodDays + " " +
                name;
    }

    protected int getIconRadiusPx() {
        return ICON_RADIUS;
    }
 }
