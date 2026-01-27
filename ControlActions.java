/**
 * A small interface that represents non-camera control actions.
 * Space implements this, so Controls can trigger app-level toggles without
 * depending on Space internals.
 */
public interface ControlActions {
    void toggleLabels();
    void toggleIcons();
    void togglePlanetOrbits();
    void toggleMoonOrbits();
    void toggleAsteroidOrbits();
    void toggleStars();
    void selectNearestBodyForInfo();
}
