import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Owns camera state + input mapping.
 */
public final class CameraController {

    private final Frustum frustum;

    // Orientation (degrees)
    private double yawDeg = 90.0;
    private double pitchDeg = 0.0;

    // Movement flags
    private boolean moveForward  = false;
    private boolean moveBackward = false;
    private boolean moveLeft     = false;
    private boolean moveRight    = false;
    private boolean moveUp       = false;
    private boolean moveDown     = false;

    private boolean keyLeft  = false;
    private boolean keyRight = false;
    private boolean keyUp    = false;
    private boolean keyDown  = false;

    // Speed control (matches the values previously in Space)
    private int speedLevel = 0;          // 0 = baseSpeed, negative = slower, positive = faster
    private final int minSpeedLevel = -4;
    private final int maxSpeedLevel = 12;
    private double baseSpeed = 500.0;    // units per second

    // Mouse drag look
    private boolean dragging = false;
    private int mouseDragTempX = -1;
    private int mouseDragTempY = -1;

    // Lock-to-body
    private boolean locked = false;
    private Body lockedBody = null;

    private double lockOffsetX = 0.0;
    private double lockOffsetY = 0.0;
    private double lockOffsetZ = 0.0;

    public CameraController(Frustum frustum) {
        this.frustum = frustum;
    }

    public double getYawDeg() { return yawDeg; }
    public double getPitchDeg() { return pitchDeg; }

    public void setYawPitchDeg(double yawDeg, double pitchDeg) {
        this.yawDeg = yawDeg;
        this.pitchDeg = clampPitch(pitchDeg);
    }

    public boolean isLocked() { return locked && lockedBody != null; }
    public Body getLockedBody() { return lockedBody; }

    public void setBaseSpeed(double baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    public int getSpeedLevel() { return speedLevel; }
    public double getBaseSpeed() { return baseSpeed; }
    public double getCurrentSpeedUnitsPerSecond() {
        return baseSpeed * java.lang.Math.pow(2.0, speedLevel);
    }

    /**
     * Called once per frame with dtSeconds (real time elapsed).
     * Updates frustum camera position and orientation based on:
     * - lock state
     * - movement keys
     * - arrow keys rotation
     */
    public void update(double dtSeconds) {
        // 1) Sync frustum orientation
        frustum.cameraYaw   = yawDeg;
        frustum.cameraPitch = pitchDeg;

        // 2) If locked, always keep camera attached to body + offset
        if (isLocked()) {
            frustum.cameraX = lockedBody.getX() + lockOffsetX;
            frustum.cameraY = lockedBody.getY() + lockOffsetY;
            frustum.cameraZ = lockedBody.getZ() + lockOffsetZ;
        }

        // 3) No movement input? Nothing more to do.
        if (!moveForward && !moveBackward &&
                !moveLeft && !moveRight &&
                !moveUp && !moveDown &&
                !keyLeft && !keyRight &&
                !keyUp && !keyDown) {
            return;
        }

        // dtSeconds = time since last frame in seconds
        double ROT_DEG_PER_SEC = 45.0; // tune

        double yawDelta   = 0.0;
        double pitchDelta = 0.0;

        if (keyLeft)  yawDelta   -= ROT_DEG_PER_SEC * dtSeconds;
        if (keyRight) yawDelta   += ROT_DEG_PER_SEC * dtSeconds;
        if (keyUp)    pitchDelta -= ROT_DEG_PER_SEC * dtSeconds;
        if (keyDown)  pitchDelta += ROT_DEG_PER_SEC * dtSeconds;

        yawDeg   = wrapYaw(yawDeg + yawDelta);
        pitchDeg = clampPitch(pitchDeg + pitchDelta);

        // 4) Compute camera basis once
        double[] forward = new double[3];
        double[] right   = new double[3];
        double[] up      = new double[3]; // not used directly, but we compute it anyway
        Frustum.computeCameraBasis(yawDeg, pitchDeg, forward, right, up);

        double fx = forward[0], fy = forward[1], fz = forward[2];
        double rx = right[0],   ry = right[1],   rz = right[2];

        // World up for vertical movement
        double upx = 0.0, upy2 = 1.0, upz = 0.0;

        // 5) Build movement direction in world space
        double moveX = 0.0, moveY = 0.0, moveZ = 0.0;

        // W/S: forward/back
        if (moveForward)  { moveX += fx; moveY += fy; moveZ += fz; }
        if (moveBackward) { moveX -= fx; moveY -= fy; moveZ -= fz; }

        // A/D: strafe
        if (moveRight) { moveX += rx; moveY += ry; moveZ += rz; }
        if (moveLeft)  { moveX -= rx; moveY -= ry; moveZ -= rz; }

        // Up/Down: world vertical
        if (moveUp)   { moveX += upx; moveY += upy2; moveZ += upz; }
        if (moveDown) { moveX -= upx; moveY -= upy2; moveZ -= upz; }

        // 6) Normalize movement vector
        double len = Math.sqrt(moveX*moveX + moveY*moveY + moveZ*moveZ);
        if (len == 0.0) return;

        moveX /= len;
        moveY /= len;
        moveZ /= len;

        double speed    = baseSpeed * java.lang.Math.pow(2.0, speedLevel);
        double distance = speed * dtSeconds;

        // 7) Apply movement:
        //    - locked: adjust offset relative to body
        //    - unlocked: adjust camera position directly
        if (isLocked()) {
            lockOffsetX += moveX * distance;
            lockOffsetY += moveY * distance;
            lockOffsetZ += moveZ * distance;

            frustum.cameraX = lockedBody.getX() + lockOffsetX;
            frustum.cameraY = lockedBody.getY() + lockOffsetY;
            frustum.cameraZ = lockedBody.getZ() + lockOffsetZ;
        } else {
            frustum.cameraX += moveX * distance;
            frustum.cameraY += moveY * distance;
            frustum.cameraZ += moveZ * distance;
        }

        // Keep orientation in sync after keyboard rotation
        frustum.cameraYaw   = yawDeg;
        frustum.cameraPitch = pitchDeg;
    }

    /** Returns true if the key was handled by the camera controller. */
    public boolean handleKeyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_W: moveForward  = true;  return true;
            case KeyEvent.VK_S: moveBackward = true;  return true;
            case KeyEvent.VK_A: moveLeft     = true;  return true;
            case KeyEvent.VK_D: moveRight    = true;  return true;

            case KeyEvent.VK_LEFT:  keyLeft  = true;  return true;
            case KeyEvent.VK_RIGHT: keyRight = true;  return true;
            case KeyEvent.VK_UP:    keyUp    = true;  return true;
            case KeyEvent.VK_DOWN:  keyDown  = true;  return true;

            case KeyEvent.VK_SPACE:   moveUp   = true; return true;
            case KeyEvent.VK_CONTROL: moveDown = true; return true;

            case KeyEvent.VK_Q:
                speedLevel = Math.max(minSpeedLevel, speedLevel - 1);
                return true;
            case KeyEvent.VK_E:
                speedLevel = Math.min(maxSpeedLevel, speedLevel + 1);
                return true;
        }
        return false;
    }

    /** Returns true if the key was handled by the camera controller. */
    public boolean handleKeyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_W: moveForward  = false; return true;
            case KeyEvent.VK_S: moveBackward = false; return true;
            case KeyEvent.VK_A: moveLeft     = false; return true;
            case KeyEvent.VK_D: moveRight    = false; return true;

            case KeyEvent.VK_LEFT:  keyLeft  = false; return true;
            case KeyEvent.VK_RIGHT: keyRight = false; return true;
            case KeyEvent.VK_UP:    keyUp    = false; return true;
            case KeyEvent.VK_DOWN:  keyDown  = false; return true;

            case KeyEvent.VK_SPACE:   moveUp   = false; return true;
            case KeyEvent.VK_CONTROL: moveDown = false; return true;
        }
        return false;
    }

    public void handleMousePressed(MouseEvent e) {
        mouseDragTempX = e.getX();
        mouseDragTempY = e.getY();
        dragging = true;
    }

    public void handleMouseReleased(MouseEvent e) {
        dragging = false;
        mouseDragTempX = -1;
        mouseDragTempY = -1;
    }

    /**
     * Mouse drag look. Pass the current viewport size so sensitivity matches the old Space impl.
     */
    public void handleMouseDragged(MouseEvent event, int viewWidth, int viewHeight) {
        if (!dragging) return;

        int x = event.getX();
        int y = event.getY();

        double movementX = (mouseDragTempX - x);
        double movementY = (mouseDragTempY - y);

        final double yawSensitivity   = 180.0;
        final double pitchSensitivity = 180.0;

        double deltaYaw   = (movementX / (double) viewWidth)  * yawSensitivity;
        double deltaPitch = (movementY / (double) viewHeight) * pitchSensitivity;

        yawDeg   = wrapYaw(yawDeg - deltaYaw);
        pitchDeg = clampPitch(pitchDeg + deltaPitch);

        mouseDragTempX = x;
        mouseDragTempY = y;
    }

    public void lockToBody(Body body) {
        if (body == null) {
            unlock();
            return;
        }

        locked = true;
        lockedBody = body;

        // Preserve your old "jump above the body by 2 radii" behavior,
        // but make it STICK by setting offsets (fixes the old snap-to-center issue).
        lockOffsetX = 0.0;
        lockOffsetY = body.radius * 2.0;
        lockOffsetZ = 0.0;

        frustum.cameraX = body.getX() + lockOffsetX;
        frustum.cameraY = body.getY() + lockOffsetY;
        frustum.cameraZ = body.getZ() + lockOffsetZ;
    }

    public void unlock() {
        locked = false;
        lockedBody = null;
        lockOffsetX = lockOffsetY = lockOffsetZ = 0.0;
    }

    private static double wrapYaw(double yawDeg) {
        double y = yawDeg % 360.0;
        if (y < 0.0) y += 360.0;
        return y;
    }

    private static double clampPitch(double pitchDeg) {
        if (pitchDeg > 89.0) return 89.0;
        if (pitchDeg < -89.0) return -89.0;
        return pitchDeg;
    }
}