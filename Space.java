import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.KeyEvent;
import static java.lang.Character.*;
import java.util.*;
import javax.swing.*;
import java.io.IOException;
import java.io.PrintWriter;

public class Space extends Canvas implements MouseMotionListener, MouseListener, KeyListener, Runnable {
	
	private Star star;
	private ArrayList<Planet> ps;
	private ArrayList<Moon> ss;
	private ArrayList<Asteroid> asteroids;
	private BufferedImage back;
	private double displaySpeed;
	
	private long lastCurrentTime;
	public long simulationTime;

	// --- Accessors for serialization / refactoring ---
	public Star getStar() { return star; }
	public ArrayList<Planet> getPlanets() { return ps; }
	public ArrayList<Moon> getMoons() { return ss; }
	public ArrayList<Asteroid> getAsteroids() { return asteroids; }
	public double getDisplaySpeed() { return displaySpeed; }
	public void setSimulationTime(long t) { simulationTime = t; }
	public void resetTimingAfterLoad() { lastCurrentTime = System.nanoTime(); }

	private int mouseDragTempX = -1;
	private int mouseDragTempY = -1;
	
	public double viewX;
	public double viewY;
	
	public static double yaw;
	public static double pitch;

	private CameraController camera;
	
	// movement flags
	private boolean moveForward  = false;
	private boolean moveBackward = false;
	private boolean moveLeft     = false;
	private boolean moveRight    = false;
	private boolean moveUp       = false;
	private boolean moveDown     = false;

	private boolean keyLeft, keyRight, keyUp, keyDown;

	// speed control
	private int speedLevel = 0;          // 0 = baseSpeed, negative = slower, positive = faster
	private final int minSpeedLevel = -4;
	private final int maxSpeedLevel = 12;
	private double baseSpeed = 500.0;   // units per second, tune to taste

	// time tracking for camera movement
	private long lastCameraUpdateTime = System.nanoTime();
	
	public static Frustum frustrum;
	
	public boolean locked = false;
	public Body lockedBody;
	
	// new: camera offset relative to locked body
	private double lockOffsetX = 0.0;
	private double lockOffsetY = 0.0;
	private double lockOffsetZ = 0.0;
	
	boolean dragging = false;
	
	public static int VIEW_WIDTH;
	public static int VIEW_HEIGHT;
	
	public static int ACTUAL_WIDTH;
	public static int ACTUAL_HEIGHT;
	
	public static final int FRAMERATE = 30;
	
	public boolean showLabels = false;
	public boolean showIcons = true;
	
	// --- Overlay decluttering (icons/labels) ---
	public boolean focusCullingEnabled = true;
	private Planet focusPlanet = null;

	// Fade icons/labels based on apparent on-screen size (pixelRadius)
	public float  overlayMinAlpha = 0.02f;
	public float  overlayMaxAlpha = 1.00f;
	public double overlayFadeRefPixelRadius = 0.75; // “almost a pixel”
	public double overlayFadeGamma = 0.1;           // >1 dims small things more

	// Focus system sizing
	private double focusSystemPadding = 5;
	private double focusFallbackRadiusMult = 250.0;
	
    private Body lastInfoBody = null;
    private long infoHudUntilNanos = 0L;
    private static final long INFO_HUD_DURATION_NANOS = 30_000_000_000L; // 3 seconds

	// --- Orbit path rendering ---
	public boolean showPlanetOrbits   = false;
	public boolean showMoonOrbits     = false;
	public boolean showAsteroidOrbits = false;

	// Starfield rendering
	private Starfield starfield;
	public boolean showStars = false;

	// Tuning
	public float  orbitBaseAlpha = 0.25f;         // overall opacity for orbit lines
	public double orbitMinVisiblePx = 2.0;        // if orbit appears smaller than this, skip
	public double orbitFadeRefPx = 25.0;          // fade-in reference scale
	public double orbitFadeGamma = 0.65;          // shape the fade

	public int orbitSegmentsMin = 48;
	public int orbitSegmentsMax = 200;

	// Reuse buffers to avoid GC
	private final double[] orbitCamTmp = new double[4];
	private final java.awt.geom.Point2D.Double orbitScreenTmp = new java.awt.geom.Point2D.Double();
	private final java.awt.geom.Point2D.Double orbitCenterScreenTmp = new java.awt.geom.Point2D.Double();

	// if you want to convert to km in HUD:
    public static final double SCALE_KM_PER_UNIT = 100.0; // keep in sync with generator

	public Space (int viewWidth, int viewHeight, int actualWidth, int actualHeight, ArrayList<Planet> ps2, Star s2, ArrayList<Moon> ss2) {
		setBackground(Color.BLACK);
		
		lastCurrentTime = System.nanoTime();
		
		ps = ps2;
		star = s2;	
		ss = ss2;
		asteroids = new ArrayList<Asteroid>();
		displaySpeed = 1;
		VIEW_WIDTH = viewWidth;
		VIEW_HEIGHT = viewHeight;
		ACTUAL_WIDTH = actualWidth;
		ACTUAL_HEIGHT = actualHeight;
		viewX = ACTUAL_WIDTH/2;
		viewY = ACTUAL_HEIGHT/2;
		
		yaw = 90;
		pitch = 0;
		
		double camDist = 10000000;

		frustrum = new Frustum(
			    70,
			    (double) VIEW_WIDTH / (double) VIEW_HEIGHT,
			    1.0,
			    ACTUAL_WIDTH * 2.0
			);

		// In the plane, to the left of the star, looking right at it
		frustrum.setCameraPosition(star.getX(), star.getY(), star.getZ() - camDist);

		camera = new CameraController(frustrum);
		camera.setYawPitchDeg(yaw, pitch);

		try {
			java.nio.file.Path p = java.nio.file.Paths.get("saves", "hyg_stars.bin");
			starfield = Starfield.loadFromFile(p);
			starfield.debugStats = true;
		} catch (IOException ex) {
			ex.printStackTrace();
			starfield = null;
		}

		System.out.println(starfield.count);

		setFocusable(true);
		setFocusTraversalKeysEnabled(true);
		requestFocusInWindow();

		this.addKeyListener(this);
		this.addMouseListener(this);
		this.addMouseMotionListener(this);
		new Thread(this).start();
	}
	
	public void update(Graphics window) {
		long t0 = System.nanoTime();
		
	    long currentTime   = System.nanoTime();
	    long durationNanos = currentTime - lastCurrentTime;
	    lastCurrentTime    = currentTime;

	    // dt in seconds for camera movement
	    double dtSeconds = durationNanos / 1_000_000_000.0;

		// advance sim time + move bodies
		simulationTime = SimulationEngine.step(
				simulationTime,
				durationNanos,
				displaySpeed,
				ps,
				ss,
				asteroids
		);

		// 2) Sync frustum orientation with current yaw/pitch
	    frustrum.cameraYaw   = yaw;
	    frustrum.cameraPitch = pitch;

	    // 3) Update camera position (locked or free) using NEW body positions
	    updateCameraPosition(dtSeconds);
	    updateFocusSystem();
	    
	    // 4) Render
	    paint(window);
	    
	    long t1 = System.nanoTime();
	    long frameMs = (t1 - t0) / 1_000_000;
	    if (frameMs > 100) { // only log spikes
	        Runtime rt = Runtime.getRuntime();
	        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
	        long total = rt.totalMemory() / (1024 * 1024);
	        System.out.println("FRAME SPIKE: " + frameMs + " ms | heap " + used + " / " + total + " MB");
	    }

	    // 5) Sleep to cap framerate
//		try {
//			Thread.sleep(1000/FRAMERATE);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}
	
	public void paint (Graphics window) {
		
		Graphics2D tdg = (Graphics2D) window;
		
		if(back==null)
		   back = (BufferedImage)(createImage(getWidth(),getHeight()));
		   
		Graphics2D gtb = back.createGraphics();
		
		gtb.setColor(Color.BLACK);
		gtb.fillRect(0,0,VIEW_WIDTH,VIEW_HEIGHT);

		// Orbits (draw behind bodies)
		drawOrbits(gtb);

		// Starfield
		if (showStars && starfield != null) {
			starfield.draw(gtb, frustrum, VIEW_WIDTH, VIEW_HEIGHT);
		}

		//add all bodies to list, sort by distance to camera, draw closest to camera first
		ArrayList<Body> drawList = new ArrayList<Body>();
		
		for (Moon s : ss) {
			drawList.add(s);
		}
		
		for (Planet p : ps) {
			drawList.add(p);
		}
		
		for (Asteroid a : asteroids) {
			drawList.add(a);
		}
		
		drawList.add(star);
		
		drawList = sortDrawList(drawList);
		
		for(Body b: drawList) {
			b.draw(gtb, this, frustrum);
		}
		
		// HUD overlay
	    gtb.setColor(Color.WHITE);
	    int hudY = 20;

		double speed = (camera != null ? camera.getCurrentSpeedUnitsPerSecond() : 0.0) * SCALE_KM_PER_UNIT;
	    double simSecondsPerSecond = displaySpeed; // because simTime += realTime * displaySpeed
	    String simSpeedStr = formatSimSpeed(simSecondsPerSecond);
	    
	    gtb.drawString("Sim Speed: " + simSpeedStr, 10, hudY);
	    hudY += 15;
		gtb.drawString(
				String.format("Speed: base * 2^%d  (%.3g km/s)", (camera != null ? camera.getSpeedLevel() : 0), speed),
				10, hudY
		);
	    hudY += 15;
	    gtb.drawString("W/S: forward/back  A/D: strafe  Space/Ctrl: up/down  Q/E: speed -/+  L: toggle labels  ?:  Show nearest body info", 10, hudY);
	    hudY += 45;
	    
	    // --- Nearest body info HUD (when '?' pressed) ---
	    if (lastInfoBody != null && lastCurrentTime < infoHudUntilNanos) {
	        double bx = lastInfoBody.getX();
	        double by = lastInfoBody.getY();
	        double bz = lastInfoBody.getZ();

	        double cx = frustrum.cameraX;
	        double cy = frustrum.cameraY;
	        double cz = frustrum.cameraZ;

	        double dx = bx - cx;
	        double dy = by - cy;
	        double dz = bz - cz;

	        double distUnits = Math.sqrt(dx*dx + dy*dy + dz*dz);
	        double distKm    = distUnits * SCALE_KM_PER_UNIT;

	        String type = lastInfoBody.getType();
	        String name = lastInfoBody.getName();
	        double radiusUnits = lastInfoBody.getRadius();
	        double radiusKm    = radiusUnits * SCALE_KM_PER_UNIT;

	        gtb.setColor(Color.WHITE);
	        gtb.drawString(
	            String.format("Nearest: %s (%s)", name, type),
	            10, hudY
	        );
	        hudY += 15;

	        gtb.drawString(
	            String.format("Distance from camera: %.3g units  (~%.3g km)", distUnits, distKm),
	            10, hudY
	        );
	        hudY += 15;

	        gtb.drawString(
	            String.format("Radius: %.3g units  (~%.3g km)", radiusUnits, radiusKm),
	            10, hudY
	        );
	        hudY += 15;

	        // Extra details if this is an orbiting body
	        if (lastInfoBody instanceof OrbitingBody) {
	            OrbitingBody ob = (OrbitingBody) lastInfoBody;
	            Body parent = ob.getParent();  // make sure OrbitingBody has this getter

	            // Orbital elements
	            double e = ob.e;                       // eccentricity
	            double periodDays  = ob.periodSeconds / 86400.0;
	            double periodYears = periodDays / 365.25;

	            String parentName = (parent != null ? parent.getName() : "None");

	            gtb.drawString(
	                String.format("Orbits: %s", parentName),
	                10, hudY
	            );
	            hudY += 15;

	            gtb.drawString(
	                String.format("Eccentricity: %.4f", e),
	                10, hudY
	            );
	            hudY += 15;

	            gtb.drawString(
	                String.format("Orbital period: %.3g days  (%.3g years)", periodDays, periodYears),
	                10, hudY
	            );
	            hudY += 15;

	            // Distance from parent *right now*
	            if (parent != null) {
	                double px = parent.getX();
	                double py = parent.getY();
	                double pz = parent.getZ();

	                double pdx = bx - px;
	                double pdy = by - py;
	                double pdz = bz - pz;

	                double parentDistUnits = Math.sqrt(pdx*pdx + pdy*pdy + pdz*pdz);
	                double parentDistKm    = parentDistUnits * SCALE_KM_PER_UNIT;

	                gtb.drawString(
	                    String.format("Current distance to parent: %.3g units  (~%.3g km)",
	                                  parentDistUnits, parentDistKm),
	                    10, hudY
	                );
	                hudY += 15;
	            }
	        }
	    }

		tdg.drawImage(back, null, 0, 0);
	}

	private void drawOrbits(Graphics2D g2) {
		if (!showPlanetOrbits && !showMoonOrbits && !showAsteroidOrbits) return;

		// We want these to obey your focus-system rule too:
		// shouldDrawOverlaysFor(...) already encodes: sun always, focus system, etc. :contentReference[oaicite:3]{index=3}
		// We’ll reuse it for orbits as well.

		java.awt.Composite oldComp = g2.getComposite();
		java.awt.Stroke oldStroke = g2.getStroke();

		g2.setStroke(new BasicStroke(1f));

		if (showPlanetOrbits) {
			for (Planet p : ps) {
				if (p == null) continue;
				if (!shouldDrawOverlaysFor(p)) continue;
				drawOrbitPathFor((OrbitingBody)p, g2, new Color(120,120,120));
			}
		}

		if (showMoonOrbits) {
			for (Moon m : ss) {
				if (m == null) continue;
				if (!shouldDrawOverlaysFor(m)) continue;
				drawOrbitPathFor((OrbitingBody)m, g2, new Color(120,120,120));
			}
		}

		if (showAsteroidOrbits) {
			for (Asteroid a : asteroids) {
				if (a == null) continue;
				if (!shouldDrawOverlaysFor(a)) continue;
				drawOrbitPathFor((OrbitingBody)a, g2, new Color(100,100,100));
			}
		}

		g2.setComposite(oldComp);
		g2.setStroke(oldStroke);
	}

	private void drawOrbitPathFor(OrbitingBody ob, Graphics2D g2, Color color) {
		Body parent = ob.getParent();
		if (parent == null) return;

		// Orbit scale in world units
		double a = ob.getSemiMajorAxis();   // you already use this for moons in estimateSystemRadiusUnits :contentReference[oaicite:5]{index=5}
		double e = ob.e;

		double apo = a * (1.0 + e);
		if (apo <= 0) return;

		// Project orbit center (parent) to estimate on-screen size
		frustrum.worldToCameraSpaceDirect(parent.getX(), parent.getY(), parent.getZ(), orbitCamTmp);
		if (!frustrum.project3DTo2D(
				orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
				VIEW_WIDTH, VIEW_HEIGHT, orbitCenterScreenTmp
		)) {
			// Center not projectable => usually orbit won’t be helpful
			return;
		}

		// Estimate pixels-per-unit using parent depth
		// We can approximate by projecting a point offset by 1 unit along camera-right.
		// (If you don’t have a “project with out param” overload, keep using the overload you used elsewhere.)
		double pxPerUnit = estimatePixelsPerUnitAt(parent.getX(), parent.getY(), parent.getZ());
		if (pxPerUnit <= 0) return;

		double orbitPx = apo * pxPerUnit;
		if (orbitPx < orbitMinVisiblePx) return;

		// Fade orbits similar in spirit to overlay fading (but tuned separately)
		double t = orbitPx / orbitFadeRefPx;
		t = Math.max(0.0, Math.min(1.0, t));
		double shaped = java.lang.Math.pow(t, orbitFadeGamma);
		float alpha = (float)(orbitBaseAlpha * shaped);
		if (alpha <= 0.001f) return;

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g2.setColor(orbitTint(ob.color, 0.25f, 70));

		// Adaptive segment count based on orbit size on screen
		int seg = (int)Math.round(orbitSegmentsMin + (orbitSegmentsMax - orbitSegmentsMin) * Math.min(1.0, orbitPx / 400.0));
		seg = Math.max(orbitSegmentsMin, Math.min(orbitSegmentsMax, seg));

		// Precompute rotation terms (typical Ω-i-ω transform)
		double cosO = Math.cos(ob.omegaBigRad);
		double sinO = Math.sin(ob.omegaBigRad);
		double cosI = Math.cos(ob.inclRad);
		double sinI = Math.sin(ob.inclRad);
		double cosw = Math.cos(ob.omegaSmallRad);
		double sinw = Math.sin(ob.omegaSmallRad);

		// We will draw segments only when consecutive points are projectable
		boolean hasPrev = false;
		int prevX = 0, prevY = 0;

		for (int i = 0; i <= seg; i++) {
			double nu = (2.0 * Math.PI) * (i / (double)seg); // true anomaly
			double r = (a * (1.0 - e*e)) / (1.0 + e * Math.cos(nu));

			// Perifocal coordinates (orbit plane)
			double xP = r * Math.cos(nu);
			double yP = r * Math.sin(nu);

			// Rotate by ω
			double x1 = xP * cosw - yP * sinw;
			double y1 = xP * sinw + yP * cosw;

			// Rotate by i (about x)
			double x2 = x1;
			double y2 = y1 * cosI;
			double z2 = y1 * sinI;

			// Rotate by Ω (about z) -> this gives ECLIPTIC frame coords (xEc, yEc, zEc)
			double xEc = x2 * cosO - y2 * sinO;
			double yEc = x2 * sinO + y2 * cosO;
			double zEc = z2;

			// ECLIPTIC -> ENGINE remap (same convention as ringNormalFromPoleRADec):
			// engine Y = ecliptic Z
			// engine Z = ecliptic Y
			double xEng = xEc;
			double yEng = zEc;
			double zEng = yEc;

			// Translate to parent position in ENGINE coords
			double wx = parent.getX() + xEng;
			double wy = parent.getY() + yEng;
			double wz = parent.getZ() + zEng;

			frustrum.worldToCameraSpaceDirect(wx, wy, wz, orbitCamTmp);
			if (!frustrum.project3DTo2D(
					orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
					VIEW_WIDTH, VIEW_HEIGHT, orbitScreenTmp
			)) {
				hasPrev = false;
				continue;
			}

			int x = (int)Math.round(orbitScreenTmp.x);
			int y = (int)Math.round(orbitScreenTmp.y);

			if (hasPrev) {
				g2.drawLine(prevX, prevY, x, y);
			}
			prevX = x; prevY = y;
			hasPrev = true;
		}
	}

	// Returns ENGINE-frame world position for a given true anomaly nu (radians)
	private static Vector3d orbitPointEngine(
			double a, double e,
			double omegaBig, double incl, double omegaSmall,
			double nu,
			Vector3d parentPos
	) {
		// radius in orbital plane
		double r = (a * (1.0 - e*e)) / (1.0 + e * Math.cos(nu));

		// orbital plane coords (perifocal): z' = 0
		double xP = r * Math.cos(nu);
		double yP = r * Math.sin(nu);

		// Precompute trig
		double cO = Math.cos(omegaBig),  sO = Math.sin(omegaBig);   // Ω
		double ci = Math.cos(incl),      si = Math.sin(incl);       // i
		double cw = Math.cos(omegaSmall),sw = Math.sin(omegaSmall); // ω

		// Perifocal -> ecliptic (standard 3-1-3: Rz(Ω) Rx(i) Rz(ω))
		double xEc =
				(cO*cw - sO*sw*ci) * xP +
						(-cO*sw - sO*cw*ci) * yP;

		double yEc =
				(sO*cw + cO*sw*ci) * xP +
						(-sO*sw + cO*cw*ci) * yP;

		double zEc =
				(sw*si) * xP +
						(cw*si) * yP;

		// ECLIPTIC -> ENGINE remap (same as ringNormalFromPoleRADec)
		double x = xEc;
		double y = zEc;  // engine Y = ecliptic Z
		double z = yEc;  // engine Z = ecliptic Y

		return new Vector3d(
				parentPos.x + x,
				parentPos.y + y,
				parentPos.z + z
		);
	}

	private double estimatePixelsPerUnitAt(double wx, double wy, double wz) {
		// project center
		frustrum.worldToCameraSpaceDirect(wx, wy, wz, orbitCamTmp);
		if (!frustrum.project3DTo2D(orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
				VIEW_WIDTH, VIEW_HEIGHT, orbitCenterScreenTmp)) {
			return 0.0;
		}

		// project a point 1 unit to the camera-right direction in world space:
		// Use Frustum basis helper (you already call computeCameraBasis elsewhere, though currently allocating arrays). :contentReference[oaicite:6]{index=6}
		double[] f = new double[3];
		double[] r = new double[3];
		double[] u = new double[3];
		Frustum.computeCameraBasis(yaw, pitch, f, r, u);

		double wx2 = wx + r[0];
		double wy2 = wy + r[1];
		double wz2 = wz + r[2];

		frustrum.worldToCameraSpaceDirect(wx2, wy2, wz2, orbitCamTmp);
		if (!frustrum.project3DTo2D(orbitCamTmp[0], orbitCamTmp[1], orbitCamTmp[2],
				VIEW_WIDTH, VIEW_HEIGHT, orbitScreenTmp)) {
			return 0.0;
		}

		double dx = orbitScreenTmp.x - orbitCenterScreenTmp.x;
		double dy = orbitScreenTmp.y - orbitCenterScreenTmp.y;
		return Math.sqrt(dx*dx + dy*dy); // pixels per 1 world unit
	}

	private static Color orbitTint(Color bodyColor, float tintAmount, int baseGray) {
		// tintAmount: 0 = pure gray, 1 = pure body color
		int r = (int)(baseGray + tintAmount * (bodyColor.getRed()   - baseGray));
		int g = (int)(baseGray + tintAmount * (bodyColor.getGreen() - baseGray));
		int b = (int)(baseGray + tintAmount * (bodyColor.getBlue()  - baseGray));
		r = Math.max(0, Math.min(255, r));
		g = Math.max(0, Math.min(255, g));
		b = Math.max(0, Math.min(255, b));
		return new Color(r, g, b);
	}

	private String formatSimSpeed(double simSecondsPerSecond) {
	    double sec = simSecondsPerSecond;

	    if (sec < 120) { 
	        return String.format("%.2f seconds/sec", sec);
	    }

	    double minutes = sec / 60.0;
	    if (minutes < 120) {
	        return String.format("%.2f minutes/sec", minutes);
	    }

	    double hours = minutes / 60.0;
	    if (hours < 48) {
	        return String.format("%.2f hours/sec", hours);
	    }

	    double days = hours / 24.0;
	    if (days < 365) {
	        return String.format("%.2f days/sec", days);
	    }

	    double years = days / 365.25;
	    return String.format("%.2f years/sec", years);
	}
	
	private void selectNearestBodyForInfo() {
	    Body nearest = null;
	    double bestDist2 = Double.POSITIVE_INFINITY;

	    double cx = frustrum.cameraX;
	    double cy = frustrum.cameraY;
	    double cz = frustrum.cameraZ;

	    // Helper lambda-like thing for each list:
	    java.util.List<Body> all = new ArrayList<>();
	    all.add(star);
	    all.addAll(ps);
	    all.addAll(ss);
	    if (asteroids != null) all.addAll(asteroids);

	    for (Body b : all) {
	        if (b == null) continue;

	        double dx = b.getX() - cx;
	        double dy = b.getY() - cy;
	        double dz = b.getZ() - cz;

	        double d2 = dx*dx + dy*dy + dz*dz;
	        if (d2 < bestDist2) {
	            bestDist2 = d2;
	            nearest = b;
	        }
	    }

	    if (nearest != null) {
	        lastInfoBody = nearest;
	        // show for a few seconds from "now"
	        infoHudUntilNanos = lastCurrentTime + INFO_HUD_DURATION_NANOS;
	    }
	}
	
	/** Decide which planet system we are “in” (or null if global view). */
	private void updateFocusSystem() {
	    if (!focusCullingEnabled) { focusPlanet = null; return; }

	    // Prefer lock state as the most intentional signal.
		if (camera != null && camera.isLocked()) {
			Body lb = camera.getLockedBody();
			if (lb instanceof Moon)   { focusPlanet = ((Moon) lb).getPlanet(); return; }
			if (lb instanceof Planet) { focusPlanet = (Planet) lb; return; }
			focusPlanet = null;
			return;
		}


		// Otherwise infer by camera proximity to each planet’s “system radius”
	    Planet best = null;
	    double bestScore = Double.POSITIVE_INFINITY;

	    for (Planet p : ps) {
	        if (p == null) continue;

	        double dx = frustrum.cameraX - p.getX();
	        double dy = frustrum.cameraY - p.getY();
	        double dz = frustrum.cameraZ - p.getZ();
	        double d  = java.lang.Math.sqrt(dx*dx + dy*dy + dz*dz);

	        double sysR = estimateSystemRadiusUnits(p);
	        if (sysR <= 0.0) continue;

	        if (d <= sysR) {
	            double score = d / sysR; // “deeper inside” => smaller
	            if (score < bestScore) { bestScore = score; best = p; }
	        }
	    }

	    focusPlanet = best;
	}

	/** System radius heuristic: furthest moon semi-major axis, padded. */
	private double estimateSystemRadiusUnits(Planet p) {
	    double maxMoonA = 0.0;
	    for (Moon m : ss) {
	        if (m == null) continue;
	        if (m.getPlanet() != p) continue;
	        maxMoonA = java.lang.Math.max(maxMoonA, m.getSemiMajorAxis());
	    }
	    if (maxMoonA > 0.0) return maxMoonA * focusSystemPadding;

	    // fallback if no moons loaded
	    return java.lang.Math.max(1.0, p.getRadius() * focusFallbackRadiusMult);
	}

	/** True if icons/labels/subpixel dots should be shown for this body right now. */
	public boolean shouldDrawOverlaysFor(Body b) {
	    if (b == null) return false;

	    // Always allow the Sun
	    if (b instanceof Star) return true;

	    if (!focusCullingEnabled) return true;
	    if (focusPlanet == null) return true; // global view

	    if (b == focusPlanet) return true;
	    if (b instanceof Moon) return ((Moon) b).getPlanet() == focusPlanet;

	    // other planets, asteroids, etc are “outside focus”
	    return false;
	}

	/** Alpha (0..1) for overlay-ish visuals based on pixelRadius. */
	public float computeOverlayAlpha(Body b, double pixelRadius) {
	    if (!shouldDrawOverlaysFor(b)) return 0.0f;

	    // Big enough to be a real disk -> don’t fade
	    if (pixelRadius >= 1.0) return 1.0f;

	    double t = pixelRadius / overlayFadeRefPixelRadius;
	    t = java.lang.Math.max(0.0, java.lang.Math.min(1.0, t));

	    double shaped = java.lang.Math.pow(t, overlayFadeGamma);
	    double a = overlayMinAlpha + (overlayMaxAlpha - overlayMinAlpha) * shaped;
	    a = java.lang.Math.max(0.0, java.lang.Math.min(1.0, a));
	    return (float) a;
	}
	
	private static ArrayList<Body> sortDrawList(ArrayList<Body> drawList) {
	    int n = drawList.size();
	    for (int i = 1; i < n; ++i) {
	        Body keyBody = drawList.get(i);

	        double bx = keyBody.getX();
	        double by = keyBody.getY();
	        double bz = keyBody.getZ();

	        double keyDist = new Vector3d(bx, by, bz)
	                .distance(new Vector3d(frustrum.cameraX, frustrum.cameraY, frustrum.cameraZ)); 

	        int j = i - 1;
	        while (j >= 0) {
	            Body jb = drawList.get(j);
	            double jbx = jb.getX();
	            double jby = jb.getY();
	            double jbz = jb.getZ();

	            double jbDist = new Vector3d(jbx, jby, jbz)
	                    .distance(new Vector3d(frustrum.cameraX, frustrum.cameraY, frustrum.cameraZ));

	            if (jbDist >= keyDist) break;

	            drawList.set(j + 1, jb);
	            j--;
	        }
	        drawList.set(j + 1, keyBody);
	    }
	    return drawList;
	}

	private void updateCameraPosition(double dtSeconds) {
		if (camera == null) return;
		camera.update(dtSeconds);

		// keep Space yaw/pitch in sync (used by orbit helpers, etc.)
		yaw = camera.getYawDeg();
		pitch = camera.getPitchDeg();
	}

	public void lockToBody(Body body) {
		if (camera != null) camera.lockToBody(body);
	}

	public void unlockBody() {
		if (camera != null) camera.unlock();
	}

	public void run() {
   		try {
	   		while(true) {
	   		   Thread.currentThread().sleep(5);
	           repaint();
	        }
      	}
      	catch(Exception e) {
      	}
  	}
  	
  	public void setDisplaySpeed(double ds) {
  		displaySpeed = ds;
  	}
  	
  	public long getSimulationTime() {
        return simulationTime;
    }

	public void keyReleased(KeyEvent e) {
		if (camera != null) camera.handleKeyReleased(e);
	}

	public void keyPressed(KeyEvent e) {
		if (camera != null && camera.handleKeyPressed(e)) {
			return;
		}

		int code = e.getKeyCode();
		switch (code) {
			case KeyEvent.VK_L:
				showLabels = !showLabels;
				break;
			case KeyEvent.VK_I:
				showIcons = !showIcons;
				break;
			case KeyEvent.VK_SLASH:
				selectNearestBodyForInfo();
			case KeyEvent.VK_1:
				showPlanetOrbits = !showPlanetOrbits;
				break;
			case KeyEvent.VK_2:
				showMoonOrbits = !showMoonOrbits;
				break;
			case KeyEvent.VK_3:
				showAsteroidOrbits = !showAsteroidOrbits;
				break;
			case KeyEvent.VK_0:
				showStars = !showStars;
				break;
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		requestFocusInWindow();
		if (camera != null) camera.handleMousePressed(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (camera != null) camera.handleMouseReleased(e);
	}

	@Override
	public void mouseDragged(MouseEvent event) {
		if (camera != null) {
			camera.handleMouseDragged(event, VIEW_WIDTH, VIEW_HEIGHT);

			// keep Space yaw/pitch in sync immediately
			yaw = camera.getYawDeg();
			pitch = camera.getPitchDeg();

			repaint();
		}
	}

	public void save(PrintWriter out) {
		SystemSerializer.save(this, out);
	}

	public void load(Scanner load) {
		SystemSerializer.load(this, load);
	}

	private static final double MIN_VISUAL_RING_WIDTH_KM = 120.0;

	private static void addRingletCenterWidthKm(
	        RingSystem rs,
	        double centerKm,
	        double widthKm,
	        int particles,
	        Color color,
	        float opticalDepth
	) {
	    double w = Math.max(widthKm, MIN_VISUAL_RING_WIDTH_KM);
	    double innerUnits = (centerKm - 0.5 * w) / Space.SCALE_KM_PER_UNIT;
	    double outerUnits = (centerKm + 0.5 * w) / Space.SCALE_KM_PER_UNIT;
	    rs.addBand(new RingSystem.RingBand(innerUnits, outerUnits, particles, color, opticalDepth));
	}

	private static void addRingRangeKm(
	        RingSystem rs,
	        double innerKm,
	        double outerKm,
	        int particles,
	        Color color,
	        float opticalDepth
	) {
	    double innerUnits = innerKm / Space.SCALE_KM_PER_UNIT;
	    double outerUnits = outerKm / Space.SCALE_KM_PER_UNIT;
	    rs.addBand(new RingSystem.RingBand(innerUnits, outerUnits, particles, color, opticalDepth));
	}
	
	private static Vector3d ringNormalFromPoleRADec(double raDeg, double decDeg) {
	    double ra  = Math.toRadians(raDeg);
	    double dec = Math.toRadians(decDeg);

	    // 1) Equatorial pole vector
	    double xEq = Math.cos(dec) * Math.cos(ra);
	    double yEq = Math.cos(dec) * Math.sin(ra);
	    double zEq = Math.sin(dec);

	    // 2) Rotate equatorial → ecliptic
	    double eps = Math.toRadians(23.439281); // J2000 obliquity
	    double xEc = xEq;
	    double yEc =  Math.cos(eps)*yEq + Math.sin(eps)*zEq;
	    double zEc = -Math.sin(eps)*yEq + Math.cos(eps)*zEq;

	    // 3) Remap to your engine frame (X, Z, Y)
	    Vector3d n = new Vector3d(
	        xEc,
	        zEc,  // engine Y = ecliptic Z
	        yEc   // engine Z = ecliptic Y
	    );

	    return n.normalize();
	}

	
	public void keyTyped(KeyEvent e) {
		
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		
	}


}