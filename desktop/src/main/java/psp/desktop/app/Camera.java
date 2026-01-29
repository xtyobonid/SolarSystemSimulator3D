package psp.desktop.app;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Camera stores position + yaw/pitch (degrees) and can produce:
 * - view matrix (world -> view)
 * - rotation-only view matrix (world -> view, no translation) for starfield
 *
 * Movement uses the INVERSE of the view rotation so WASD stays consistent
 * with what you see after rotating the camera.
 */
public final class Camera {
    private final Vector3f pos = new Vector3f();
    private float yawDeg = 0f;
    private float pitchDeg = 0f;

    // Cached matrices:
    // viewRot: world -> view rotation (no translation)
    // invViewRot: view -> world rotation (used for movement basis)
    private final Matrix4f viewRot = new Matrix4f();
    private final Matrix4f invViewRot = new Matrix4f();
    private boolean rotDirty = true;

    public void setPosition(float x, float y, float z) { pos.set(x, y, z); }
    public Vector3f position() { return pos; }

    public void setYawPitchDeg(float yaw, float pitch) {
        yawDeg = wrapYaw(yaw);
        pitchDeg = clampPitch(pitch);
        rotDirty = true;
    }

    public void addYawPitchDeg(float yawDelta, float pitchDelta) {
        yawDeg = wrapYaw(yawDeg + yawDelta);
        pitchDeg = clampPitch(pitchDeg + pitchDelta);
        rotDirty = true;
    }

    private float clampPitch(float p) {
        return Math.max(-89.9f, Math.min(89.9f, p));
    }

    private float wrapYaw(float y) {
        float r = y % 360.0f;
        if (r < 0) r += 360.0f;
        return r;
    }

    private void ensureRotationUpToDate() {
        if (!rotDirty) return;

        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);

        // This MUST match how we build the view matrix below.
        // Rotation that transforms WORLD -> VIEW.
        viewRot.identity()
                .rotateX(pitchRad)
                .rotateY(yawRad);

        // Movement wants VIEW -> WORLD
        invViewRot.set(viewRot).invert();

        rotDirty = false;
    }

    /**
     * Move in camera-local space.
     * forward/right are relative to camera orientation; up/down is WORLD up (Y axis),
     * matching your original project behavior.
     */
    public void moveLocal(double forward, double right, double up, double distance) {
        if (forward == 0 && right == 0 && up == 0) return;

        ensureRotationUpToDate();

        Vector3f fwd = new Vector3f(0, 0, -1);
        invViewRot.transformDirection(fwd);

        Vector3f rgt = new Vector3f(1, 0, 0);
        invViewRot.transformDirection(rgt);

        Vector3f upp = new Vector3f(0, 1, 0);

        pos.fma((float) (forward * distance), fwd);
        pos.fma((float) (right   * distance), rgt);
        pos.fma((float) (up      * distance), upp);
    }

    // View matrix (includes translation): world -> view
    public void buildViewMatrix(Matrix4f out) {
        ensureRotationUpToDate();
        out.set(viewRot)
                .translate(-pos.x, -pos.y, -pos.z);
    }

    // Rotation-only view (no translation) for starfield: world -> view
    public void buildViewRotationMatrix(Matrix4f out) {
        ensureRotationUpToDate();
        out.set(viewRot);
    }

    public void transformViewToWorldDirection(Vector3f dir) {
        ensureRotationUpToDate();
        invViewRot.transformDirection(dir);
    }
}
