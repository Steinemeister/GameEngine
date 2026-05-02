package engine.rendering;

import engine.EngineManager;
import engine.object.Model;
import logger.Logger;
import org.lwjgl.opengl.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.glfw.GLFW.*;

public class RenderManager implements Runnable{
    private Logger logger = EngineManager.logger;

    private long windowHandle;

    //temp
    Model model;

    @Override
    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) {
            logger.error(new IllegalStateException("unable to initialize GLFW"));
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        windowHandle = glfwCreateWindow(2000, 1000, "GameEngine", NULL, NULL);
        if (windowHandle == NULL) {
            logger.error(new RuntimeException("unable to create window"));
        }

        glfwMakeContextCurrent(windowHandle);
        glfwShowWindow(windowHandle);

        GL.createCapabilities();
        logger.info("OpenGL Version: " + glGetString(GL_VERSION));


        float[] vertices = {
                -0.5f, -0.5f, 0.0f, // Unten links
                0.5f, -0.5f, 0.0f, // Unten rechts
                0.0f,  0.5f, 0.0f  // Oben Mitte
        };



        model = new Model(vertices);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);


    }

    private void loop() {
        glClearColor(0.1f, 0.1f, 0.1f, 0.1f);

        while (!glfwWindowShouldClose(windowHandle)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            int vao = glGenVertexArrays();
            glBindVertexArray(vao);

// 2. VBO erstellen und Daten hochladen
            int vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, model.getVertices(), GL_STATIC_DRAW);

            glVertexAttribPointer(0,  3, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(0);

            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, model.getVertices().length / 3); // 3 ist die Anzahl der Vertices
            glBindVertexArray(0);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);

            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
    }
}


