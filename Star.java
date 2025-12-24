import java.awt.*;
import java.awt.geom.Point2D;
import java.lang.Math;

public class Star extends Body {
	
	private static final int ICON_RADIUS = 12;
	
	public Star (double wWidth, double wHeight, double radius) {
		this.x = wWidth / 2;
		this.z = wHeight / 2;
		this.y = 0.0;
        this.radius = radius;
        this.color = new Color(255, 255, 255);
        this.name  = "Sun";
        this.type  = "star";
	}
	
	protected int getIconRadiusPx() {
        return ICON_RADIUS;
    }
}