import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

    // Optional convenience index (rebuild when loading/changing names)
    private final HashMap<String, Planet> planetByName = new HashMap<>();

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
        planetByName.clear();
    }

    public Planet getPlanetByName(String name) {
        Planet p = planetByName.get(name);
        if (p != null) return p;

        // Fallback (in case index wasn't rebuilt yet)
        for (Planet pl : planets) {
            if (pl.getName().equals(name)) return pl;
        }
        return null;
    }
}
