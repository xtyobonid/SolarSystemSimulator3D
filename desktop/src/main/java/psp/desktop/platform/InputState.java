package psp.desktop.platform;

import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;

public final class InputState {
    private final long window;

    private final boolean[] keysNow = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keysPrev = new boolean[GLFW_KEY_LAST + 1];

    private final boolean[] mouseNow = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mousePrev = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    public InputState(long window) {
        this.window = window;
    }

    public void poll() {
        // keys
        for (int k = 32; k <= GLFW_KEY_LAST; k++) {
            keysNow[k] = glfwGetKey(window, k) == GLFW_PRESS;
        }
        // mouse buttons
        for (int b = 0; b <= GLFW_MOUSE_BUTTON_LAST; b++) {
            mouseNow[b] = glfwGetMouseButton(window, b) == GLFW_PRESS;
        }

        // mouse cursor
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            mouseX = x.get(0);
            mouseY = y.get(0);
        }

        if (firstMouse) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            firstMouse = false;
        }
    }

    public void endFrame() {
        System.arraycopy(keysNow, 0, keysPrev, 0, keysNow.length);
        System.arraycopy(mouseNow, 0, mousePrev, 0, mouseNow.length);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    // Key state
    public boolean keyDown(int glfwKey) { return keysNow[glfwKey]; }
    public boolean keyPressed(int glfwKey) { return keysNow[glfwKey] && !keysPrev[glfwKey]; }

    // Mouse state
    public boolean mouseDown(int glfwButton) { return mouseNow[glfwButton]; }
    public boolean mousePressed(int glfwButton) { return mouseNow[glfwButton] && !mousePrev[glfwButton]; }

    // Mouse delta
    public double mouseDx() { return mouseX - lastMouseX; }
    public double mouseDy() { return mouseY - lastMouseY; }
}
