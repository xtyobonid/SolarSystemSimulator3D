import java.util.List;

/**
 * Pure simulation stepping:
 * - Advances simulation time (scaled by displaySpeed)
 * - Moves orbiting bodies to their positions at the new simulation time
 *
 * This is intentionally renderer-agnostic and UI-agnostic.
 */
public final class SimulationEngine {

    private SimulationEngine() {}

    /**
     * Advances simulation time and updates all orbiting bodies to their new positions.
     *
     * @param simulationTimeNanos current simulation time (nanoseconds)
     * @param realDeltaNanos      real time elapsed since last frame (nanoseconds)
     * @param displaySpeed        simulation seconds per real second (or scale factor on nanoseconds)
     * @return new simulationTimeNanos
     */
    public static long step(
            long simulationTimeNanos,
            long realDeltaNanos,
            double displaySpeed,
            List<Planet> planets,
            List<Moon> moons,
            List<Asteroid> asteroids
    ) {
        // simulationTime += durationNanos * displaySpeed;  (compound assignment truncates)
        long simDeltaNanos = (long) (realDeltaNanos * displaySpeed);
        long newSimTime = simulationTimeNanos + simDeltaNanos;

        // Move bodies to their positions at this simulation time
        for (Planet p : planets) p.move(newSimTime);
        for (Moon m : moons) m.move(newSimTime);
        for (Asteroid a : asteroids) a.move(newSimTime);

        return newSimTime;
    }
}
