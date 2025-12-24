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
	
	private int mouseDragTempX = -1;
	private int mouseDragTempY = -1;
	
	public double viewX;
	public double viewY;
	
	public static double yaw;
	public static double pitch;
	
	// movement flags
	private boolean moveForward  = false;
	private boolean moveBackward = false;
	private boolean moveLeft     = false;
	private boolean moveRight    = false;
	private boolean moveUp       = false;
	private boolean moveDown     = false;

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

	    // advance simulation time (scaled by displaySpeed)
	    simulationTime += durationNanos * displaySpeed;

	    // 1) Move bodies to their positions at this simulationTime
	    for (Planet p : ps) {
	        p.move(simulationTime);
	    }
	    for (Moon s : ss) {
	        s.move(simulationTime);
	    }
	    for (Asteroid a : asteroids) {
	    	a.move(simulationTime);
	    }

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
		gtb.fillRect(0,0,VIEW_WIDTH * 2,VIEW_HEIGHT * 2);
		
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

	    double speed = baseSpeed * java.lang.Math.pow(2.0, speedLevel);
	    double simSecondsPerSecond = displaySpeed; // because simTime += realTime * displaySpeed
	    String simSpeedStr = formatSimSpeed(simSecondsPerSecond);
	    
	    gtb.drawString("Sim Speed: " + simSpeedStr, 10, hudY);
	    hudY += 15;
	    gtb.drawString(
	    	    String.format("Speed: base * 2^%d  (%.3g units/s)", speedLevel, speed),
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
	    if (locked && lockedBody != null) {
	        if (lockedBody instanceof Moon)   { focusPlanet = ((Moon) lockedBody).getPlanet(); return; }
	        if (lockedBody instanceof Planet) { focusPlanet = (Planet) lockedBody; return; }
	        // locked to Sun or asteroid -> treat as global
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
	    // 1) If locked, always keep camera attached to body + offset,
	    //    even when not moving.
	    if (locked && lockedBody != null) {
	        frustrum.cameraX = lockedBody.getX() + lockOffsetX;
	        frustrum.cameraY = lockedBody.getY() + lockOffsetY;
	        frustrum.cameraZ = lockedBody.getZ() + lockOffsetZ;
	    }

	    // 2) No movement input? Nothing more to do.
	    if (!moveForward && !moveBackward &&
	        !moveLeft && !moveRight &&
	        !moveUp && !moveDown) {
	        return;
	    }

	    // 3) Compute camera basis once
	    double[] forward = new double[3];
	    double[] right   = new double[3];
	    double[] up      = new double[3]; // not used directly, but we compute it anyway

	    Frustum.computeCameraBasis(yaw, pitch, forward, right, up);

	    double fx = forward[0], fy = forward[1], fz = forward[2];
	    double rx = right[0],   ry = right[1],   rz = right[2];

	    // World up for vertical movement
	    double upx = 0.0, upy = 1.0, upz = 0.0;

	    // 4) Build movement direction in world space (same for locked/unlocked)
	    double moveX = 0.0, moveY = 0.0, moveZ = 0.0;

	    // W/S: forward/back
	    if (moveForward) {
	        moveX += fx; moveY += fy; moveZ += fz;
	    }
	    if (moveBackward) {
	        moveX -= fx; moveY -= fy; moveZ -= fz;
	    }

	    // A/D: strafe
	    if (moveRight) {
	        moveX += rx; moveY += ry; moveZ += rz;
	    }
	    if (moveLeft) {
	        moveX -= rx; moveY -= ry; moveZ -= rz;
	    }

	    // Up/Down: world vertical
	    if (moveUp) {
	        moveX += upx; moveY += upy; moveZ += upz;
	    }
	    if (moveDown) {
	        moveX -= upx; moveY -= upy; moveZ -= upz;
	    }

	    // 5) Normalize movement vector
	    double len = Math.sqrt(moveX*moveX + moveY*moveY + moveZ*moveZ);
	    if (len == 0.0)
	        return;

	    moveX /= len;
	    moveY /= len;
	    moveZ /= len;

	    double speed    = baseSpeed * java.lang.Math.pow(2.0, speedLevel);
	    double distance = speed * dtSeconds;

	    // 6) Apply movement:
	    //    - locked: adjust offset relative to body
	    //    - unlocked: adjust camera position directly
	    if (locked && lockedBody != null) {
	        // Move the offset around the body
	        lockOffsetX += moveX * distance;
	        lockOffsetY += moveY * distance;
	        lockOffsetZ += moveZ * distance;

	        // Recompute camera from updated offset
	        frustrum.cameraX = lockedBody.getX() + lockOffsetX;
	        frustrum.cameraY = lockedBody.getY() + lockOffsetY;
	        frustrum.cameraZ = lockedBody.getZ() + lockOffsetZ;
	    } else {
	        // Free-fly mode
	        frustrum.cameraX += moveX * distance;
	        frustrum.cameraY += moveY * distance;
	        frustrum.cameraZ += moveZ * distance;
	    }
	}

	
	public void lockToBody(Body body) {
	    if (body == null) {
	        locked = false;
	        lockedBody = null;
	        return;
	    }

	    locked = true;
	    lockedBody = body;

	    frustrum.cameraX = body.getX();
	    frustrum.cameraY = body.getY() + body.radius * 2;
	    frustrum.cameraZ = body.getZ();
	}
	
	public void unlockBody() {
	    locked = false;
	    lockedBody = null;
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
  	    int code = e.getKeyCode();
  	    switch (code) {
  	        case KeyEvent.VK_W: moveForward  = false; break;
  	        case KeyEvent.VK_S: moveBackward = false; break;
  	        case KeyEvent.VK_A: moveLeft     = false; break;
  	        case KeyEvent.VK_D: moveRight    = false; break;
  	        case KeyEvent.VK_SPACE: moveUp       = false; break;
  	        case KeyEvent.VK_CONTROL: moveDown     = false; break;
  	    }
  	}
	
	public void keyPressed(KeyEvent e) {
	    int code = e.getKeyCode();
	    switch (code) {
	        case KeyEvent.VK_W: moveForward  = true; break;
	        case KeyEvent.VK_S: moveBackward = true; break;
	        case KeyEvent.VK_A: moveLeft     = true; break;
	        case KeyEvent.VK_D: moveRight    = true; break;
	        case KeyEvent.VK_SPACE: moveUp       = true; break;
	        case KeyEvent.VK_CONTROL: moveDown     = true; break;
	        case KeyEvent.VK_Q:
	            speedLevel = Math.max(minSpeedLevel, speedLevel - 1);
	            break;
	        case KeyEvent.VK_E:
	            speedLevel = Math.min(maxSpeedLevel, speedLevel + 1);
	            break;
	        case KeyEvent.VK_L:
	            showLabels = !showLabels;
	            break;
	        case KeyEvent.VK_I:
	        	showIcons = !showIcons;
	        	break;
	        case KeyEvent.VK_SLASH:
	        	selectNearestBodyForInfo();
	    }
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
	    Point point = e.getPoint();
	    mouseDragTempX = point.x;
	    mouseDragTempY = point.y;
	    dragging = true;
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	    dragging = false;
	    mouseDragTempX = -1;
	    mouseDragTempY = -1;
	}
	
	@Override
	public void mouseDragged(MouseEvent event) {
	    if (dragging) {
	        Point point = event.getPoint();

	        double movementX = (mouseDragTempX - point.x);
	        double movementY = (mouseDragTempY - point.y);

	        final double yawSensitivity   = 180.0; // tweak to taste
	        final double pitchSensitivity = 180.0;

	        double deltaYaw   = (movementX / (double) VIEW_WIDTH)  * yawSensitivity;
	        double deltaPitch = (movementY / (double) VIEW_HEIGHT) * pitchSensitivity;

	        // invert signs if you want opposite feel
	        yaw   -= deltaYaw;
	        pitch += deltaPitch;

	        // wrap yaw
	        if (yaw < 0.0)      yaw += 360.0;
	        if (yaw >= 360.0)   yaw -= 360.0;

	        // clamp pitch
	        if (pitch > 89.0)   pitch = 89.0;
	        if (pitch < -89.0)  pitch = -89.0;

	        mouseDragTempX = point.x;
	        mouseDragTempY = point.y;

	        repaint();
	    }
	}
	
	public void save(PrintWriter save) {
		save.println(simulationTime);
		save.println(ps.size());
		for(int i = 0; i < ps.size(); i++) {
//			save.println(ps.get(i).save());
		}
		save.println(ss.size());
		for(int i = 0; i < ss.size(); i++) {
//			save.println(ss.get(i).save());
		}
		
		save.close();
	}
	
	public void load(Scanner load) {
		simulationTime = Long.parseLong(load.next());
		load.nextLine();
		load.nextLine();
		
		int numPlanets = Integer.parseInt(load.next());
		load.nextLine();
		
		for (int i = 0; i < numPlanets; i++) {
			String line = load.nextLine();
			Planet p = new Planet(line, star, displaySpeed);
			ps.add(p);
		}
		
		// Rings
		// Ratio of particles for Saturn : Uranus : Neptune should be 1 : 0.3 : 0.1
		for (Planet p : ps) {
		    String n = p.getName();

		    if ("Saturn".equalsIgnoreCase(n)) {
		        double km = 1.0 / Space.SCALE_KM_PER_UNIT;

		        Vector3d saturnNormal = ringNormalFromPoleRADec(40.589, 83.537);

		        RingSystem saturnRings = new RingSystem(p, -2.0e-5, saturnNormal );

		        // C ring (dimmer)
		        saturnRings.addBand(new RingSystem.RingBand(74_658 * km, 92_000 * km, 3000, new Color(190,180,165), 0.35f));

		        // B ring (brightest)
		        saturnRings.addBand(new RingSystem.RingBand(92_000 * km, 117_580 * km, 5000, new Color(225,215,195), 0.75f));

		        // Cassini division (very sparse / dark)
		        saturnRings.addBand(new RingSystem.RingBand(117_580 * km, 122_170 * km, 800, new Color(170,165,155), 0.10f));

		        // A ring (bright)
		        saturnRings.addBand(new RingSystem.RingBand(122_170 * km, 136_775 * km, 4000, new Color(220,210,190), 0.60f));

		        p.setRings(saturnRings);
		    }

		    if ("Uranus".equalsIgnoreCase(n)) {
		        double innerKm   = 38_000.0;
		        double outerKm   = 51_000.0;
		        double innerUnits = innerKm / Space.SCALE_KM_PER_UNIT;
		        double outerUnits = outerKm / Space.SCALE_KM_PER_UNIT;

		        Vector3d uranusNormal  = ringNormalFromPoleRADec(257.311, -15.175);

		        RingSystem uranusRings = new RingSystem(
		                p,
		                innerUnits,
		                outerUnits,
		                3000,
		                new Color(190, 200, 210),
		                -2.5e-5,
		                uranusNormal
		        );
		        p.setRings(uranusRings);
		    }

		    if ("Neptune".equalsIgnoreCase(n)) {
		        double innerKm   = 42_000.0;
		        double outerKm   = 63_000.0;
		        double innerUnits = innerKm / Space.SCALE_KM_PER_UNIT;
		        double outerUnits = outerKm / Space.SCALE_KM_PER_UNIT;

		        Vector3d neptuneNormal = ringNormalFromPoleRADec(299.36,  43.46);

		        RingSystem neptuneRings = new RingSystem(
		                p,
		                innerUnits,
		                outerUnits,
		                1000,
		                new Color(180, 190, 200),
		                -2.5e-5,
		                neptuneNormal
		        );
		        p.setRings(neptuneRings);
		    }
		}
		
		int numMoons = Integer.parseInt(load.next());
		load.nextLine();
		
		for (int i = 0; i < numMoons; i++) {
			String line = load.nextLine();
			String planetName = line.substring(line.lastIndexOf(" ") + 1);
			
			Planet p = ps.get(0);
			for (int j = 1; j < ps.size(); j++) {
				if (ps.get(j).getName().equals(planetName)) {
					p = ps.get(j);
				}
			}
			
			Moon m = new Moon(line.substring(0, line.lastIndexOf(" ")), p, displaySpeed);
			ss.add(m);
		}
		
		int numAsteroids = Integer.parseInt(load.next());
		load.nextLine();

		for (int i = 0; i < numAsteroids; i++) {
		    String line = load.nextLine();
		    Asteroid a = new Asteroid(line, star, displaySpeed);
		    asteroids.add(a);
		}
		
		load.close();
		
		lastCurrentTime = System.nanoTime();
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