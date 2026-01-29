package psp.desktop;

import psp.desktop.platform.GlfwWindow;

public final class Main {
    public static void main(String[] args) {
        try (GlfwWindow window = new GlfwWindow("PSP OpenGL", 1280, 720)) {
            window.runLoop();
        }
    }
}
