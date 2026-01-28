import java.awt.Graphics;

/**
 * Rendering abstraction.
 *
 * For now this targets AWT (Graphics). Later will add an LWJGL/OpenGL renderer
 * (e.g., OpenGLRenderer) and swap implementations without touching simulation.
 */
public interface Renderer {
    void render(Graphics window, Space space);
}