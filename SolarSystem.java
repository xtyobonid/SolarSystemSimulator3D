import java.util.ArrayList;
import java.util.HashMap;

/**
 * Central "world state" for the simulator/builder.
 *
 * Owns:
 * - Star
 * - Lists of bodies (planets, moons, asteroids)
 * - Common lookups (planet by name)
 */
public final class SolarSystem {

    private Star star;

    private final ArrayList<Planet> planets = new ArrayList<>();
    private final ArrayList<Moon> moons = new ArrayList<>();
    private final ArrayList<Asteroid> asteroids = new ArrayList<>();

    public SolarSystem(Star star) {
        this.star = star;
    }

    public Star getStar() { return star; }
    public void setStar(Star star) { this.star = star; }

    public ArrayList<Planet> getPlanets() { return planets; }
    public ArrayList<Moon> getMoons() { return moons; }
    public ArrayList<Asteroid> getAsteroids() { return asteroids; }

    public void clearAll() {
        planets.clear();
        moons.clear();
        asteroids.clear();
    }
}
