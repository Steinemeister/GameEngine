package engine.rendering;

import engine.main.GameSettings;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.glfw.GLFW.*;

public class Window {
    private long windowHandle = NULL;

    public void create() {
        // 1. WICHTIG: Einen Error-Callback setzen, damit GLFW Fehler direkt in die Konsole druckt!
        GLFWErrorCallback.createPrint(System.err).set();

        // 2. WICHTIG: GLFW initialisieren. Ohne das gibt glfwCreateWindow immer NULL zurück!
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW konnte nicht initialisiert werden!");
        }

        // 3. Window-Hints setzen (Konfiguration für OpenGL 3.3 Core Profile)
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Fenster erst verstecken, bis der Render-Thread bereit ist
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Wichtig für macOS Support

        // 4. Fenster erstellen
        windowHandle = glfwCreateWindow(
                GameSettings.windowWidth,
                GameSettings.windowHeight,
                "Voxel Engine",
                NULL,
                NULL
        );

        // Prüfen, ob die Erstellung geklappt hat
        if (windowHandle == NULL) {
            throw new RuntimeException("Fehler: Das GLFW-Fenster konnte nicht erstellt werden! " +
                    "Unterstützt deine Grafikkarte OpenGL 3.3?");
        }

        // 5. Fenster auf dem Bildschirm zentrieren (Optional, aber schön)
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(windowHandle, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            if (vidmode != null) {
                glfwSetWindowPos(
                        windowHandle,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        }

        // 6. WICHTIG FÜR MULTITHREADING:
        // Der Kontext wird beim Erstellen automatisch an den Main-Thread gebunden.
        // Wir MÜSSEN ihn hier lösen, damit der Render-Thread ihn sich schnappen darf!
        glfwMakeContextCurrent(NULL);
    }

    public void updateEvents() {
        glfwPollEvents();
    }

    public void swapBuffers() {
        if (windowHandle != NULL) {
            glfwSwapBuffers(windowHandle);
        }
    }

    public boolean shouldClose() {
        // Falls das Handle aus irgendeinem Grund NULL ist, schließen wir sicherheitshalber
        if (windowHandle == NULL) return true;
        return glfwWindowShouldClose(windowHandle);
    }

    public long getHandle() {
        return windowHandle;
    }

    public void destroy() {
        if (windowHandle != NULL) {
            glfwDestroyWindow(windowHandle);
        }
        glfwTerminate();

        var callback = glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }
}
