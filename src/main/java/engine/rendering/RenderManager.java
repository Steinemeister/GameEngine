package engine.rendering;

import engine.EngineManager;
import engine.lighting.PointLight;
import engine.util.*;
import engine.util.Object;
import logger.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.glfw.GLFW.*;

public class RenderManager implements Runnable{
    private final Logger logger = EngineManager.logger;

    private long window;
    private int width = 1;
    private int height = 1;
    private float aspectRatio;

    private double lastFrameTime = 0.0;
    private float deltaTime = 1.0f;

    public void run() {
        init();
        loop();

        // Cleanup
        glfwTerminate();
    }

    private void init() {
        if (!glfwInit()) logger.error(new IllegalStateException("unable to initialize GLFW"));

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);


        window = glfwCreateWindow(width, height, "OpenGL based engine", NULL, NULL);
        if (window == NULL) logger.error(new RuntimeException("unable to create window"));

        glfwSetWindowSizeLimits(window, 640, 480, GLFW_DONT_CARE, GLFW_DONT_CARE);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // V-Sync
        GL.createCapabilities();

        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetWindowSize(window, w, h);
        this.width = w[0];
        this.height = h[0];

        this.aspectRatio = (float) width / height;


        glfwSetFramebufferSizeCallback(window, (windowHandle, newWidth, newHeight) -> {
           glViewport(0, 0, newWidth, newHeight);

            this.width = newWidth;
            this.height = newHeight;
            this.aspectRatio = (float) width / height;
        });

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
    }

    private void loop() {
        ShaderPipeline shadowShader = new ShaderPipeline(
                "src/main/resources/shaders/shadow_v.glsl",
                "src/main/resources/shaders/shadow_f.glsl",
                "src/main/resources/shaders/shadow_g.glsl",
                null, null
        );

        ShaderPipeline mainShader = new ShaderPipeline(
                "src/main/resources/shaders/main_v.glsl",
                "src/main/resources/shaders/main_f.glsl"
        );

        List<PointLight> pointLights = new ArrayList<>();

        pointLights.add(new PointLight(
                new Vector3f(2.0f, 5.0f, 2.0f),
                new Vector3f(1.0f, 1.0f, 1.0f),
                1.0f
        ));

        List<Object> sceneObjects = new ArrayList<>();


        Mesh cubeMesh = ObjectLoader.loadOBJ("src/main/resources/models/Cube.obj");
        Mesh floorMesh = ObjectLoader.loadOBJ("src/main/resources/models/floor.obj");

        Object cube = new Object(cubeMesh);
        cube.getPosition().set(0, 1, 0);
        sceneObjects.add(cube);

        Object floor = new Object(floorMesh);
        floor.getPosition().set(0, 0, 0);
        floor.setScale(10.0f);
        sceneObjects.add(floor);

        Object secondCube = new Object(cubeMesh);
        secondCube.getPosition().set(-3, 0.5f, 2);
        secondCube.getRotation().set(0, 45, 0);
        sceneObjects.add(secondCube);

        Camera camera = new Camera();

        // Initialisierung Shadow-Buffer für jedes Licht (hier Beispiel mit einem Licht)
        PointShadowBuffer shadowBuffer = new PointShadowBuffer();
        float farPlane = 25.0f;
        InputManager input = new InputManager(window);

        lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {

            double thisFrameTime = glfwGetTime();
            deltaTime = (float) (thisFrameTime - lastFrameTime);
            lastFrameTime = thisFrameTime;

            camera.handleInput(input, deltaTime);

            // --- PHASE 1: SHADOW PASS ---
            shadowShader.bind();
            for (int i = 0; i < pointLights.size(); i++) {
                PointLight light = pointLights.get(i);
                shadowBuffer.bind();
                glClear(GL_DEPTH_BUFFER_BIT); // Nur Tiefe löschen

                Vector3f lightPos = light.position;
                shadowShader.setUniform("lightPos", lightPos);
                shadowShader.setUniform("far_plane", farPlane);
                shadowShader.setUniform("shadowMatrices", shadowBuffer.getShadowMatrices(lightPos));

                glCullFace(GL_FRONT); // Vorderseiten im Shadow Pass entfernen
                renderSceneSimple(shadowShader, sceneObjects);
                glCullFace(GL_BACK);
                shadowBuffer.unbind(width, height);
            }


            // --- PHASE 2: LIGHTING PASS ---
            glViewport(0, 0, width, height); // Viewport zurück auf Fenstergröße!
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            mainShader.bind();
            // Cubemap an Textur-Slot binden (z.B. Slot 1)
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_CUBE_MAP, shadowBuffer.getDepthCubemap());
            mainShader.setUniform("shadowMaps[0]", 1); // Zeigt auf Slot 1

            mainShader.setUniform("far_plane", farPlane);
            mainShader.setUniform("viewPos", camera.getPosition());

            // Finale Szene rendern
            renderSceneFull(mainShader, sceneObjects, camera, pointLights);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderSceneSimple(ShaderPipeline shadowShader, List<Object> sceneObjects) {
        for (Object object : sceneObjects) {
            // Setze die Model-Matrix, damit der Shader weiß, wo das Objekt steht
            shadowShader.setUniform("model", object.getModelMatrix());

            // Rendert das Mesh (VAO Bind -> DrawElements -> VAO Unbind)
            object.getMesh().render();
        }
    }

    private void renderSceneFull(ShaderPipeline shader, List<Object> sceneObjects, Camera camera, List<PointLight> pointLights) {

        shader.bind();
        camera.update(aspectRatio);


        shader.setUniform("viewPos", camera.getPosition());

        shader.setUniform("projection", camera.getProjection());
        shader.setUniform("view", camera.getView());

        sceneObjects.forEach(object -> {
            shader.setUniform("model", object.getModelMatrix());

            shader.setLights(pointLights);

            object.getMesh().render();
        });


        shader.unbind();


    }
}