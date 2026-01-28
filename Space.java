import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.io.IOException;
import java.io.PrintWriter;

public class Space extends Canvas implements MouseMotionListener, MouseListener, KeyListener, Runnable, ControlActions {
	
	private final Star star;
	private final ArrayList<Planet> ps;
	private final ArrayList<Moon> ss;
	private final ArrayList<Asteroid> asteroids;
	private final SolarSystem model;

	private double displaySpeed;
	
	private long lastCurrentTime;
	public long simulationTime;

	private final Controls controls = new Controls();
	private final Renderer renderer = new SoftwareRenderer();

	public double viewX;
	public double viewY;

	public static double yaw;
	public static double pitch;
	public static Frustum frustum;
	private final CameraController camera;
	
	public static int VIEW_WIDTH;
	public static int VIEW_HEIGHT;
	public static int ACTUAL_WIDTH;
	public static int ACTUAL_HEIGHT;
	
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
	private final double focusSystemPadding = 5;
	private final double focusFallbackRadiusMult = 250.0;
	
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

	// if you want to convert to km in HUD:
    public static final double SCALE_KM_PER_UNIT = 100.0; // keep in sync with generator

	// --- Accessors for serialization / refactoring ---
	public Star getStar() { return star; }
	public ArrayList<Planet> getPlanets() { return ps; }
	public ArrayList<Moon> getMoons() { return ss; }
	public ArrayList<Asteroid> getAsteroids() { return asteroids; }

	public Starfield getStarfield() { return starfield; }

	public double getDisplaySpeed() { return displaySpeed; }
	public double getBaseSpeed() { return camera.getBaseSpeed(); }
	public int getSpeedLevel() { return camera.getSpeedLevel(); }

	public Body getLastInfoBody() { return lastInfoBody; }
	public long getInfoHudUntilNanos() { return infoHudUntilNanos; }
	public long getLastCurrentTime() { return lastCurrentTime; }

	public void setSimulationTime(long t) { simulationTime = t; }
	public void resetTimingAfterLoad() { lastCurrentTime = System.nanoTime(); }

	public Space (int viewWidth, int viewHeight, int actualWidth, int actualHeight, SolarSystem model) {
		setBackground(Color.BLACK);
		
		lastCurrentTime = System.nanoTime();

		this.model = model;

		this.ps = model.getPlanets();
		this.ss = model.getMoons();
		this.asteroids = model.getAsteroids();
		this.star = model.getStar();

		displaySpeed = 1;
		VIEW_WIDTH = viewWidth;
		VIEW_HEIGHT = viewHeight;
		ACTUAL_WIDTH = actualWidth;
		ACTUAL_HEIGHT = actualHeight;
		viewX = (double) ACTUAL_WIDTH /2;
		viewY = (double) ACTUAL_HEIGHT /2;
		
		yaw = 90;
		pitch = 0;
		
		double camDist = 10000000;

		frustum = new Frustum(
			    70,
			    (double) VIEW_WIDTH / (double) VIEW_HEIGHT,
			    1.0,
			    ACTUAL_WIDTH * 2.0
			);

		// In the plane, to the left of the star, looking right at it
		frustum.setCameraPosition(star.getX(), star.getY(), star.getZ() - camDist);

		camera = new CameraController(frustum);
		camera.setYawPitchDeg(yaw, pitch);

		try {
			java.nio.file.Path p = java.nio.file.Paths.get("saves", "hyg_stars.bin");
			starfield = Starfield.loadFromFile(p);
			starfield.debugStats = true;
		} catch (IOException ex) {
			ex.printStackTrace();
			starfield = null;
		}

        if (starfield != null) {
            System.out.println(starfield.count);
        }

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
	    frustum.cameraYaw   = yaw;
	    frustum.cameraPitch = pitch;

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
	}

	public void paint(Graphics window) {
		renderer.render(window, this);
	}

	private void selectNearestBodyForInfoInternal() {
	    Body nearest = null;
	    double bestDist2 = Double.POSITIVE_INFINITY;

	    double cx = frustum.cameraX;
	    double cy = frustum.cameraY;
	    double cz = frustum.cameraZ;

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

	public SolarSystem getModel() { return model; }

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

	        double dx = frustum.cameraX - p.getX();
	        double dy = frustum.cameraY - p.getY();
	        double dz = frustum.cameraZ - p.getZ();
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

	    // other planets, asteroids, etc. are “outside focus”
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
        if (controls != null) {
            controls.handleKeyReleased(e, camera);
        }
    }

	public void keyPressed(KeyEvent e) {
        if (controls != null) {
            controls.handleKeyPressed(e, camera, this);
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

	@Override
	public void toggleLabels() {
		showLabels = !showLabels;
	}

	@Override
	public void toggleIcons() {
		showIcons = !showIcons;
	}

	@Override
	public void togglePlanetOrbits() {
		showPlanetOrbits = !showPlanetOrbits;
	}

	@Override
	public void toggleMoonOrbits() {
		showMoonOrbits = !showMoonOrbits;
	}

	@Override
	public void toggleAsteroidOrbits() {
		showAsteroidOrbits = !showAsteroidOrbits;
	}

	@Override
	public void toggleStars() {
		showStars = !showStars;
	}

	@Override
	public void selectNearestBodyForInfo() {
		selectNearestBodyForInfoInternal();
	}

	public void save(PrintWriter out) {
		SystemSerializer.save(this, out);
	}

	public void load(Scanner load) {
		SystemSerializer.load(this, load);
	}
	
	public void keyTyped(KeyEvent e) {}

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mouseMoved(MouseEvent e) {}

}