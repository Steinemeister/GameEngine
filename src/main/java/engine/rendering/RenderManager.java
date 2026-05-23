package engine.rendering;

import engine.lighting.PointLight;
import engine.object.Material;
import engine.object.Mesh;
import engine.object.Texture;
import engine.util.*;
import engine.object.Object;
import engine.world.Level;
import engine.world.VoxelChunk;
import logger.Logger;
import logger.LoggerFactory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.*;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.glfw.GLFW.*;

public class RenderManager implements Runnable {
    private Logger logger;

    private long window;
    private int width = 640;  // Standardwerte erhöht für besseres Fallback
    private int height = 480;
    private float aspectRatio;

    private double lastFrameTime = 0.0;
    private float deltaTime = 1.0f;

    public void run() {
        init();
        loop();

        // Cleanup
        logger.info("terminating GLFW");
        glfwTerminate();
    }

    private void init() {
        logger = LoggerFactory.getLogger("renderLogger");
        logger.info("initializing renderer");

        if (!glfwInit()) {
            logger.error("unable to initialize GLFW");
            throw new IllegalStateException("unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);

        // NULL-Konstante aus GLFW / LWJGL3
        window = glfwCreateWindow(width, height, "Voxel Level Engine", 0L, 0L);
        if (window == 0L) {
            logger.error("unable to create window");
            throw new RuntimeException("unable to create window");
        }

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
            logger.info("resizing framebuffer to new size of: " + newWidth + "x" + newHeight);
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
        logger.info("starting rendering loop");

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

        // Textur für das gesamte Voxel-Level laden
        Texture levelTexture = new Texture("src/main/resources/textures/Cube.png");
        Material levelMaterial = new Material(levelTexture);

        // Da wir sceneObjects entfernt haben, binden wir die Level-Textur direkt im Loop manuell,
        // um Konflikte mit dem alten Callback-Interface zu vermeiden.
        mainShader.setUniformBindCallback(((object, camera) -> {
            // Unused, da wir keine separaten Szene-Objekte mehr rendern
        }));

        List<PointLight> pointLights = new ArrayList<>();
        pointLights.add(new PointLight(
                new Vector3f(8.0f, 20.0f, 8.0f), // Position erhöht, passend für ein Voxel-Grid
                new Vector3f(1.0f, 1.0f, 1.0f),
                1.0f
        ));

        // NEU: Level mit z.B. 16x16x16 Chunk-Dimensionen initialisieren
        Level level = new Level(new Vector3i(16, 16, 16));

        // Test-Generierung: Ein paar Beispiel-Chunks laden und befüllen
        VoxelChunk chunk0 = level.loadChunk(0, 0, 0);
        chunk0.fill(); // Macht den ersten Chunk komplett solide

        Camera camera = new Camera();
        InputManager input = new InputManager(window);
        lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            double thisFrameTime = glfwGetTime();
            deltaTime = (float) (thisFrameTime - lastFrameTime);
            lastFrameTime = thisFrameTime;

            // Licht sanft im Kreis rotieren lassen
            pointLights.getFirst().position.rotateAxis((float) Math.toRadians(deltaTime * 15), 0, 1, 0);

            camera.handleInput(input, deltaTime);
            camera.update(aspectRatio);

            // 1. Schatten-Pass (Level wirft Schatten auf sich selbst)
            renderLevelShadows(level, pointLights, shadowShader);

            // 2. Haupt-Render-Pass
            glViewport(0, 0, width, height);
            bindShadowMaps(pointLights, mainShader);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Level auf den Bildschirm zeichnen
            renderLevel(level, mainShader, camera, levelMaterial);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        logger.info("stopping rendering loop");
        level.cleanup(); // Wichtig: Buffer des Levels beim Beenden freigeben
    }

    private void bindShadowMaps(List<PointLight> lights, ShaderPipeline mainPipeline) {
        // 1. Die Shader-Pipeline aktivieren, für die die Uniforms gelten sollen
        mainPipeline.bind();

        // 2. Über alle verfügbaren Punktlichter iterieren
        for (int i = 0; i < lights.size(); i++) {
            PointLight light = lights.get(i);

            // Jeder Schatten-Map eine eigene Textur-Einheit zuweisen (GL_TEXTURE0, GL_TEXTURE1, ...)
            glActiveTexture(GL_TEXTURE0 + i);

            // Die 3D-Tiefen-Cubemap des aktuellen Lichts an diese Einheit binden
            glBindTexture(GL_TEXTURE_CUBE_MAP, light.fbo.depthCubemap);

            // Dem Shader mitteilen, auf welchem Textur-Slot die Schatten-Map dieses Lichts liegt
            mainPipeline.setUniform("lights[" + i + "].shadowMap", i);

            // Die Position des Lichts an den Shader übergeben
            mainPipeline.setUniform("lights[" + i + "].position", light.position);

            // Die Lichtfarbe basierend auf der Intensität berechnen und übergeben
            org.joml.Vector3f finalColor = light.color.mul(light.intensity, new org.joml.Vector3f());
            mainPipeline.setUniform("lights[" + i + "].color", finalColor);
        }
    }

    /**
     * Rendert das Voxel-Level aus Sicht der Lichtquellen in die Cube-Shadow-Maps.
     */
    private void renderLevelShadows(Level level, List<PointLight> lights, ShaderPipeline shadowPipeline) {
        shadowPipeline.bind();
        glEnable(GL_DEPTH_TEST);

        for (PointLight light : lights) {
            glViewport(0, 0, light.fbo.shadowMapSize, light.fbo.shadowMapSize);
            light.fbo.bind();
            glClear(GL_DEPTH_BUFFER_BIT);

            Matrix4f[] transforms = calculateShadowTransforms(light.position);
            for (int i = 0; i < 6; i++) {
                shadowPipeline.setUniform("shadowMatrices[" + i + "]", transforms[i]);
            }
            shadowPipeline.setUniform("lightPos", light.position);
            shadowPipeline.setUniform("far_plane", Constants.ZFar);

            // Level in die Shadow-Map zeichnen (Camera = null, da Lichtperspektive genutzt wird)
            renderLevel(level, shadowPipeline, null, null);

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private Matrix4f[] calculateShadowTransforms(Vector3f lightPos) {
        float aspect = 1.0f;
        float near = 1.0f;
        float far = 100.0f;
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(90.0f), aspect, near, far);

        return new Matrix4f[]{
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(1, 0, 0), new Vector3f(0, -1, 0)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(-1, 0, 0), new Vector3f(0, -1, 0)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(0, 1, 0), new Vector3f(0, 0, 1)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(0, -1, 0), new Vector3f(0, 0, -1)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(0, 0, 1), new Vector3f(0, -1, 0)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(0, 0, -1), new Vector3f(0, -1, 0))
        };
    }

    private void renderLevel(Level level, ShaderPipeline pipeline, Camera camera, Material material) {

        //glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthFunc(GL_LESS);

        // 1. Shader-Programm aktivieren
        pipeline.bind();


        // 2. Material-Textur an Slot 10 binden (analog zu deinem alten Shader-Callback)
        if (material != null && material.diffuseTexture() != null) {
            material.diffuseTexture().bind(10);
            pipeline.setUniform("diffuseTexture", 10);
        }

        // 3. Kamera-Matrizen an den Shader übertragen (falls vorhanden, z.B. nicht im Shadow-Pass)
        if (camera != null) {
            pipeline.setUniform("view", camera.getView());
            pipeline.setUniform("projection", camera.getProjection());
            pipeline.setUniform("far_plane", Constants.ZFar);
        }

        // 4. Model-Matrix setzen. Da der ChunkMeshGenerator die Welt-Koordinaten der
        // Blöcke bereits direkt in die Vertices einrechnet, nutzen wir hier eine Identitätsmatrix.
        org.joml.Matrix4f identityModelMatrix = new org.joml.Matrix4f();
        pipeline.setUniform("model", identityModelMatrix);

        // 5. Über alle geladenen Chunks iterieren und deren Grafik-Mesh zeichnen
        for (VoxelChunk chunk : level.getActiveChunks().values()) {
            Mesh chunkMesh = chunk.getMesh();

            // Nur rendern, wenn der Chunk ein gültiges Mesh besitzt und nicht komplett leer ist
            if (chunkMesh != null && chunkMesh.vertexCount() > 0) {
                chunkMesh.render();
            }
        }
    }
}
