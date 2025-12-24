import java.awt.Color;
import java.util.Scanner;

public class Asteroid extends OrbitingBody {
    private static final int ASTEROID_ICON_RADIUS = 2;

    public Asteroid(String serialization, Star star, double displaySpeed) {
        super(star);

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
        this.type = "asteroid";

        load.close();

        this.inclRad       = Math.toRadians(iDeg);
        this.omegaBigRad   = Math.toRadians(omegaDeg);
        this.omegaSmallRad = Math.toRadians(wDeg);
        this.M0Rad         = Math.toRadians(M0Deg);

        this.periodSeconds = periodDays * 86400.0;
        this.meanMotion    = (2.0 * Math.PI) / this.periodSeconds;
    }

    @Override
    protected int getIconRadiusPx() {
        return ASTEROID_ICON_RADIUS;
    }
}
