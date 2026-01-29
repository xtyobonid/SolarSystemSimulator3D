import java.awt.event.KeyEvent;

/**
 * Centralized keybinds + routing.
 *
 * - Camera movement/rotation/speed are handled by CameraController
 * - App toggles are handled by ControlActions (implemented by Space)
 */
public final class Controls {

    // App-level toggles
    public static final int KEY_TOGGLE_LABELS         = KeyEvent.VK_L;
    public static final int KEY_TOGGLE_ICONS          = KeyEvent.VK_I;
    public static final int KEY_SELECT_NEAREST_INFO   = KeyEvent.VK_SLASH; // '/'
    public static final int KEY_TOGGLE_PLANET_ORBITS  = KeyEvent.VK_1;
    public static final int KEY_TOGGLE_MOON_ORBITS    = KeyEvent.VK_2;
    public static final int KEY_TOGGLE_AST_ORBITS     = KeyEvent.VK_3;
    public static final int KEY_TOGGLE_STARS          = KeyEvent.VK_0;

    /**
     * Returns true if the key was handled by either camera or app-level controls.
     */
    public boolean handleKeyPressed(KeyEvent e, CameraController camera, ControlActions app) {
        // 1) Give camera first right-of-refusal
        if (camera != null && camera.handleKeyPressed(e)) return true;

        // 2) App-level actions
        int code = e.getKeyCode();
        if (app == null) return false;

        switch (code) {
            case KEY_TOGGLE_LABELS:
                app.toggleLabels();
                return true;

            case KEY_TOGGLE_ICONS:
                app.toggleIcons();
                return true;

            case KEY_SELECT_NEAREST_INFO:
                app.selectNearestBodyForInfo();
                return true; // IMPORTANT: prevents fall-through into other toggles

            case KEY_TOGGLE_PLANET_ORBITS:
                app.togglePlanetOrbits();
                return true;

            case KEY_TOGGLE_MOON_ORBITS:
                app.toggleMoonOrbits();
                return true;

            case KEY_TOGGLE_AST_ORBITS:
                app.toggleAsteroidOrbits();
                return true;

            case KEY_TOGGLE_STARS:
                app.toggleStars();
                return true;
        }

        return false;
    }

    /**
     * Returns true if the key was handled.
     */
    public boolean handleKeyReleased(KeyEvent e, CameraController camera) {
        if (camera != null && camera.handleKeyReleased(e)) return true;
        return false;
    }
}
