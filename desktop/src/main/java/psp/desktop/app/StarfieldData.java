package psp.desktop.app;

import java.nio.FloatBuffer;
import java.util.Random;
import org.lwjgl.BufferUtils;

/**
 * Starfield stored as vec4 per star:
 *   xyz = unit direction
 *   w   = brightness (0..1)
 */
public final class StarfieldData {
    public final FloatBuffer stars; // vec4 packed
    public final int count;

    private StarfieldData(FloatBuffer stars, int count) {
        this.stars = stars;
        this.count = count;
    }

    public static StarfieldData generateDeterministic(int count, long seed) {
        Random rng = new Random(seed);
        FloatBuffer buf = BufferUtils.createFloatBuffer(count * 4);

        for (int i = 0; i < count; i++) {
            // Uniform direction on sphere
            double z = rng.nextDouble() * 2.0 - 1.0;
            double t = rng.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            float x = (float) (r * Math.cos(t));
            float y = (float) (z);
            float w = (float) (r * Math.sin(t));

            // Brightness distribution: lots of dim, few bright
            float brightness = (float) Math.pow(rng.nextDouble(), 6.0); // heavy tail
            brightness = 0.15f + 0.85f * brightness;

            buf.put(x).put(y).put(w).put(brightness);
        }

        buf.flip();
        return new StarfieldData(buf, count);
    }
}
