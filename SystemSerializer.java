import java.io.PrintWriter;
import java.util.Scanner;
import java.util.HashMap;
import java.awt.Color;

/**
 * Persistence for the solar system "save file" format currently used by PSP/Space.
 */
public final class SystemSerializer {

    private SystemSerializer() {}

    public static void save(Space space, PrintWriter out) {
        out.println(space.simulationTime);

        // --- Planets ---
        out.println(space.getPlanets().size());
        for (Planet p : space.getPlanets()) out.println(p.save());

        // --- Rings ---
        int ringCount = 0;
        for (Planet p : space.getPlanets()) {
            RingSystem rs = p.getRings();
            if (rs != null && !rs.getBands().isEmpty()) ringCount++;
        }
        out.println(ringCount);

        for (Planet p : space.getPlanets()) {
            RingSystem rs = p.getRings();
            if (rs == null || rs.getBands().isEmpty()) continue;

            Vector3d n = rs.getNormal();

            // Header format (one line):
            // planetName angularSpeed nx ny nz shadowBrightness shadowSoftness bandCount
            out.println(
                    p.getName() + " " +
                            rs.getAngularSpeed() + " " +
                            n.x + " " + n.y + " " + n.z + " " +
                            rs.ringShadowBrightness + " " +
                            rs.ringShadowSoftness + " " +
                            rs.getBands().size()
            );

            // Band format (one line each):
            // innerRadius outerRadius particleCount colorRGB opticalDepth
            for (RingSystem.RingBand b : rs.getBands()) {
                out.println(
                        b.innerRadius + " " +
                                b.outerRadius + " " +
                                b.particleCount + " " +
                                b.color.getRGB() + " " +
                                b.opticalDepth
                );
            }
        }

        // --- Moons ---
        out.println(space.getMoons().size());
        for (Moon m : space.getMoons()) {
            // Append host planet name as LAST TOKEN (current format).
            out.println(m.save() + " " + m.getPlanetName());
        }

        // --- Asteroids ---
        out.println(space.getAsteroids().size());
        for (Asteroid a : space.getAsteroids()) out.println(a.save());

        out.close();
    }

    public static void load(Space space, Scanner load) {
        space.getPlanets().clear();
        space.getMoons().clear();
        space.getAsteroids().clear();

        String line;

        // --- simulation time (first data line) ---
        line = nextDataLine(load);
        if (line == null) return;
        space.setSimulationTime(Long.parseLong(line));

        // --- planets ---
        line = nextDataLine(load);
        if (line == null) return;
        int numPlanets = Integer.parseInt(line);

        for (int i = 0; i < numPlanets; i++) {
            String pLine = nextDataLine(load);
            if (pLine == null) return;
            Planet p = new Planet(pLine, space.getStar());
            space.getPlanets().add(p);
        }

        // --- Rings (loaded from file) ---
        line = nextDataLine(load);
        if (line == null) return;
        int numRingSystems = Integer.parseInt(line);

        HashMap<String, Planet> planetByName = new HashMap<>();
        for (Planet p : space.getPlanets()) planetByName.put(p.getName(), p);

        for (int r = 0; r < numRingSystems; r++) {
            String header = nextDataLine(load);
            if (header == null) return;

            Scanner hs = new Scanner(header);

            String planetName = hs.next();
            double angularSpeed = hs.nextDouble();

            double nx = hs.nextDouble();
            double ny = hs.nextDouble();
            double nz = hs.nextDouble();

            float shadowBrightness = hs.nextFloat();
            double shadowSoftness = hs.nextDouble();

            int bandCount = hs.nextInt();
            hs.close();

            Planet planet = planetByName.get(planetName);

            // Always consume band lines even if planet is missing
            if (planet == null) {
                for (int i = 0; i < bandCount; i++) nextDataLine(load);
                continue;
            }

            RingSystem rs = new RingSystem(planet, angularSpeed, new Vector3d(nx, ny, nz));
            rs.ringShadowBrightness = shadowBrightness;
            rs.ringShadowSoftness = shadowSoftness;

            for (int i = 0; i < bandCount; i++) {
                String bandLine = nextDataLine(load);
                if (bandLine == null) return;

                Scanner bs = new Scanner(bandLine);
                double inner = bs.nextDouble();
                double outer = bs.nextDouble();
                int particleCount = bs.nextInt();
                int rgb = bs.nextInt();
                float opticalDepth = bs.nextFloat();
                bs.close();

                rs.addBand(new RingSystem.RingBand(
                        inner, outer, particleCount,
                        new Color(rgb, true),
                        opticalDepth
                ));
            }

            planet.setRings(rs);
        }

        // --- moons ---
        line = nextDataLine(load);
        if (line == null) return;
        int numMoons = Integer.parseInt(line);

        for (int i = 0; i < numMoons; i++) {
            String mLine = nextDataLine(load);
            if (mLine == null) return;

            String planetName = mLine.substring(mLine.lastIndexOf(" ") + 1);

            Planet p = space.getPlanets().isEmpty() ? null : space.getPlanets().get(0);
            for (int j = 1; j < space.getPlanets().size(); j++) {
                if (space.getPlanets().get(j).getName().equals(planetName)) {
                    p = space.getPlanets().get(j);
                }
            }
            if (p == null) return; // no planets to attach to

            Moon m = new Moon(mLine.substring(0, mLine.lastIndexOf(" ")), p);
            space.getMoons().add(m);
        }

        // --- asteroids ---
        line = nextDataLine(load);
        if (line == null) return;
        int numAsteroids = Integer.parseInt(line);

        for (int i = 0; i < numAsteroids; i++) {
            String aLine = nextDataLine(load);
            if (aLine == null) return;

            Asteroid a = new Asteroid(aLine, space.getStar());
            space.getAsteroids().add(a);
        }

        load.close();
        space.resetTimingAfterLoad();
    }

    // Reads the next meaningful line:
    // - skips blank lines
    // - skips comment lines starting with '#'
    private static String nextDataLine(Scanner sc) {
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line == null) return null;
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;
            return line;
        }
        return null;
    }
}
