package psp.desktop.app;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import psp.desktop.platform.InputState;

import static org.lwjgl.glfw.GLFW.*;

public final class SimulationScene {
    private int viewportW;
    private int viewportH;

    private final Camera camera = new Camera();
    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f viewRotationOnly = new Matrix4f();

    private double simulationTimeSeconds = 0.0;

    private final StarfieldData starfield = StarfieldData.generateDeterministic(20_000, 1337L);
    private final ArrayList<RenderBody> bodies = new ArrayList<>();

    // Lock state
    private int lockedBodyIndex = -1;           // -1 means unlocked
    private final Vector3f lockOffset = new Vector3f(0, 0, 8000); // camera = bodyPos + offset

    // Tuning
    private float mouseSensitivity = 0.12f;   // degrees per pixel-ish
    private double baseMoveSpeed = 800.0;     // units/sec (remember: 1 unit = 100 km)
    private int speedLevel = 0;              // Q/E adjusts: speed = base * 2^speedLevel

    public SimulationScene(int viewportW, int viewportH) {
        this.viewportW = viewportW;
        this.viewportH = viewportH;

        camera.setPosition(0, 0, 8000); // start “back a bit”
        camera.setYawPitchDeg(180f, 0f);

        rebuildProjection();

        // --- Test bodies (world units; 1 unit = 100 km) ---
        bodies.add(new RenderBody(
                0.0, 0.0, 0.0,
                6963.4f,   // Sun radius (696,340 km / 100)
                1.0f, 0.95f, 0.75f,
                true
        ));

        // Earth-ish (not accurate orbit yet, just a target to test scale)
        bodies.add(new RenderBody(
                149_6000.0, 0.0, 0.0,  // 149,600,000 km / 100 = 1,496,000 units
                63.71f,              // 6,371 km / 100
                0.25f, 0.45f, 1.0f,
                false
        ));

        // Jupiter-ish
        bodies.add(new RenderBody(
                -778_5000.0, 0.0, 0.0, // 778,500,000 km / 100 = 7,785,000 units
                699.11f,              // 69,911 km / 100
                0.9f, 0.75f, 0.55f,
                false
        ));

    }

    public void setViewport(int w, int h) {
        viewportW = Math.max(1, w);
        viewportH = Math.max(1, h);
        rebuildProjection();
    }

    private void rebuildProjection() {
        float aspect = (float) viewportW / (float) viewportH;
        proj.identity().perspective((float) Math.toRadians(70.0), aspect, 0.1f, 10_000_000f);
    }

    public List<RenderBody> bodies() { return bodies; }

    public float camX() { return camera.position().x; }
    public float camY() { return camera.position().y; }
    public float camZ() { return camera.position().z; }

    private boolean isLocked() {
        return lockedBodyIndex >= 0 && lockedBodyIndex < bodies.size();
    }

    private void lockToIndex(int idx) {
        if (bodies.isEmpty()) return;
        lockedBodyIndex = Math.floorMod(idx, bodies.size());

        RenderBody b = bodies.get(lockedBodyIndex);

        // offset = cameraPos - bodyPos
        lockOffset.set(
                camera.position().x - (float) b.x,
                camera.position().y - (float) b.y,
                camera.position().z - (float) b.z
        );

        System.out.println("Locked to body #" + lockedBodyIndex);
    }

    private void unlock() {
        lockedBodyIndex = -1;
        System.out.println("Unlocked");
    }

    private int findNearestBodyIndex() {
        if (bodies.isEmpty()) return -1;

        float cx = camera.position().x;
        float cy = camera.position().y;
        float cz = camera.position().z;

        int bestIdx = 0;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (int i = 0; i < bodies.size(); i++) {
            RenderBody b = bodies.get(i);
            double dx = b.x - cx;
            double dy = b.y - cy;
            double dz = b.z - cz;
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Apply a movement delta to the lockOffset in camera-local space.
     * This matches unlocked movement: forward/right relative to camera, up is world-up.
     */
    private void moveLockOffsetLocal(double forward, double right, double up, double distance) {
        if (forward == 0 && right == 0 && up == 0) return;

        // Basis vectors in VIEW space (camera space), converted to WORLD space.
        Vector3f fwd = new Vector3f(0, 0, -1);
        camera.transformViewToWorldDirection(fwd);

        Vector3f rgt = new Vector3f(1, 0, 0);
        camera.transformViewToWorldDirection(rgt);

        Vector3f upp = new Vector3f(0, 1, 0); // world up (no roll)

        lockOffset.fma((float) (forward * distance), fwd);
        lockOffset.fma((float) (right   * distance), rgt);
        lockOffset.fma((float) (up      * distance), upp);
    }

    private void applyLockPosition() {
        if (!isLocked()) return;
        RenderBody b = bodies.get(lockedBodyIndex);
        camera.setPosition(
                (float) b.x + lockOffset.x,
                (float) b.y + lockOffset.y,
                (float) b.z + lockOffset.z
        );
    }

    public void tick(double dt, InputState input) {
        simulationTimeSeconds += dt;

        // --- Lock controls ---
        // F: lock nearest
        if (input.keyPressed(GLFW_KEY_F)) {
            int idx = findNearestBodyIndex();
            if (idx >= 0) lockToIndex(idx);
        }

        // TAB: cycle lock target (next); SHIFT+TAB previous
        if (input.keyPressed(GLFW_KEY_TAB)) {
            if (!isLocked()) {
                int idx = findNearestBodyIndex();
                if (idx >= 0) lockToIndex(idx);
            } else {
                boolean shift = input.keyDown(GLFW_KEY_LEFT_SHIFT) || input.keyDown(GLFW_KEY_RIGHT_SHIFT);
                lockToIndex(lockedBodyIndex + (shift ? -1 : 1));
            }
        }

        // G: unlock
        if (input.keyPressed(GLFW_KEY_G)) {
            unlock();
        }


        // Speed controls (Q/E)
        if (input.keyPressed(GLFW_KEY_Q)) speedLevel = Math.max(-10, speedLevel - 1);
        if (input.keyPressed(GLFW_KEY_E)) speedLevel = Math.min( 20, speedLevel + 1);
        double moveSpeed = baseMoveSpeed * Math.pow(2.0, speedLevel);

        // Mouse look (hold RMB)
        if (input.mouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
            float dx = (float) input.mouseDx();
            float dy = (float) input.mouseDy();

            camera.addYawPitchDeg(
                    -dx * mouseSensitivity,
                    -dy * mouseSensitivity
            );
        }

        // Movement (WASD + Space/Ctrl)
        double forward = 0, right = 0, up = 0;
        if (input.keyDown(GLFW_KEY_W)) forward += 1;
        if (input.keyDown(GLFW_KEY_S)) forward -= 1;
        if (input.keyDown(GLFW_KEY_D)) right += 1;
        if (input.keyDown(GLFW_KEY_A)) right -= 1;
        if (input.keyDown(GLFW_KEY_SPACE)) up += 1;
        if (input.keyDown(GLFW_KEY_LEFT_CONTROL)) up -= 1;

        // Shift for temporary boost
        if (input.keyDown(GLFW_KEY_LEFT_SHIFT)) moveSpeed *= 4.0;

        double moveDelta = moveSpeed * dt;

        if (isLocked()) {
            moveLockOffsetLocal(forward, right, up, moveDelta);
            applyLockPosition();
        } else {
            camera.moveLocal(forward, right, up, moveDelta);
        }

        // Build matrices
        camera.buildViewMatrix(view);
        camera.buildViewRotationMatrix(viewRotationOnly);
    }

    // Access for renderer
    public Matrix4f projection() { return proj; }
    public Matrix4f viewMatrix() { return view; }

    /** Use for starfield: rotation only (no translation), so it stays “at infinity”. */
    public Matrix4f viewRotationOnly() { return viewRotationOnly; }

    public Camera camera() { return camera; }
    public StarfieldData starfield() { return starfield; }

    public double simulationTimeSeconds() { return simulationTimeSeconds; }
    public int viewportW() { return viewportW; }
    public int viewportH() { return viewportH; }
    public int speedLevel() { return speedLevel; }
}
