import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Scanner;
import java.lang.Math;

public class Planet extends OrbitingBody {
	private static final int ICON_RADIUS = 5;
	
	private RingSystem rings;
	
	//load planet
	public Planet (String serialization, Star star, double displaySpeed) {
	    super(star);

	    Scanner load = new Scanner(serialization);

	    // Initial position from file (we will overwrite in move(), but can store)
	    x = load.nextDouble();
	    y = load.nextDouble();
	    z = load.nextDouble();

	    radius = load.nextDouble();
	    int red   = load.nextInt();
	    int green = load.nextInt();
	    int blue  = load.nextInt();
	    color = new Color(red, green, blue);

	    // orbital elements
	    a = load.nextDouble();          // sim units
	    e = load.nextDouble();
	    double iDeg     = load.nextDouble();
	    double omegaDeg = load.nextDouble(); // Ω
	    double wDeg     = load.nextDouble(); // ω
	    double M0Deg    = load.nextDouble();
	    double periodDays = load.nextDouble();

	    name = load.next();
	    type = "planet";

	    load.close();

	    // convert to radians/seconds
	    inclRad       = Math.toRadians(iDeg);
	    omegaBigRad   = Math.toRadians(omegaDeg);
	    omegaSmallRad = Math.toRadians(wDeg);
	    M0Rad         = Math.toRadians(M0Deg);

	    periodSeconds = periodDays * 86400.0;
	    meanMotion    = (2.0 * Math.PI) / periodSeconds;
	}
	
	public void draw(Graphics g, Space s, Frustum frustum) {
        // draw the planet itself
        super.draw(g, s, frustum);

        // then draw rings if any
        if (rings != null) {
            rings.draw(g, s, frustum, s.getSimulationTime());
        }
    }
	
	public Planet (Star star) {
		super(star);
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

	public void setRings(RingSystem rings) {
        this.rings = rings;
    }

	public RingSystem getRings() { return this.rings; }

	protected int getIconRadiusPx() {
        return ICON_RADIUS;
    }
}