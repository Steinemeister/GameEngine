package engine.rendering;

import engine.EngineManager;
import engine.util.Camera;
import engine.util.Mesh;
import engine.util.ObjectLoader;
import logger.Logger;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.glfw.GLFW.*;

public class RenderManager implements Runnable{
    private Logger logger = EngineManager.logger;

    private long window;
    private int width = 800;
    private int height = 600;

    public void run() {
        init();
        loop();

        // Cleanup
        glfwTerminate();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("GLFW konnte nicht initialisiert werden");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(width, height, "OpenGL based engine", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Fenster-Erstellung fehlgeschlagen");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // V-Sync
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST); // Wichtig für 3D
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
    }

    private void loop() {
        // 1. Komponenten laden
        ShaderProgram shader = new ShaderProgram("src/main/shaders/vertex.glsl", "src/main/shaders/fragment.glsl");
        Mesh cubeMesh = ObjectLoader.loadOBJ("src/main/models/Cube.obj");
        Camera camera = new Camera();

        Matrix4f modelMatrix = new Matrix4f();
        FloatBuffer fb = org.lwjgl.BufferUtils.createFloatBuffer(16);

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.bind();

            // Kamera updaten (für den Fall von Window-Resizing)
            camera.update(width, height);
            camera.upload(shader.getProgramId()); // Methode in Camera ggf. anpassen

            // Würfel rotieren lassen
            modelMatrix.identity().rotate((float) glfwGetTime(), 0, 1, 0);
            int modelLoc = shader.getUniformLocation("model");
            glUniformMatrix4fv(modelLoc, false, modelMatrix.get(fb));

            // Rendern
            cubeMesh.render();

            shader.unbind();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        // Cleanup der Ressourcen
        cubeMesh.cleanup();
        shader.cleanup();
    }
}


