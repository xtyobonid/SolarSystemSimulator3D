package psp.desktop.platform;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import psp.desktop.app.SimulationScene;
import psp.desktop.render.OpenGLRenderer;
import psp.desktop.render.Renderer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class GlfwWindow implements AutoCloseable {
    private final long handle;

    private final InputState input;
    private final SimulationScene scene;
    private final Renderer renderer;

    public GlfwWindow(String title, int width, int height) {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        // OpenGL 3.3 core baseline (good for Windows)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        long win = glfwCreateWindow(width, height, title, NULL, NULL);
        if (win == NULL) throw new RuntimeException("Failed to create GLFW window");
        this.handle = win;

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1); // vsync on
        glfwShowWindow(handle);

        GL.createCapabilities();

        // Resize callback -> update scene viewport
        glfwSetFramebufferSizeCallback(handle, (w, newW, newH) -> {
            scene().setViewport(newW, newH);
        });

        this.input = new InputState(handle);
        this.scene = new SimulationScene(width, height);
        this.renderer = new OpenGLRenderer();
    }

    private SimulationScene scene() { return scene; }

    public void runLoop() {
        double last = glfwGetTime();

        while (!glfwWindowShouldClose(handle)) {
            double now = glfwGetTime();
            double dt = now - last;
            last = now;

            glfwPollEvents();
            input.poll();

            if (input.keyPressed(GLFW_KEY_ESCAPE)) {
                glfwSetWindowShouldClose(handle, true);
            }

            scene.tick(dt, input);
            renderer.render(scene);

            glfwSwapBuffers(handle);
            input.endFrame();
        }
    }

    @Override
    public void close() {
        glfwDestroyWindow(handle);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
