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

        TextureAtlasGenerator.generateAtlas(1);

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
        Texture textureAtlas = new Texture("src/main/generated/textureAtlas.png");


        List<PointLight> pointLights = new ArrayList<>();
        pointLights.add(new PointLight(
                new Vector3f(8.0f, 20.0f, 8.0f), // Position erhöht, passend für ein Voxel-Grid
                new Vector3f(1.0f, 1.0f, 1.0f),
                1.0f
        ));

        Level level = new Level(new Vector3i(16, 16, 16));

        int worldRadiusX = 2;
        int worldRadiusZ = 2;
        int worldHeightY = 2;

        for (int cx = -worldRadiusX; cx <= worldRadiusX; cx++) {
            for (int cz = -worldRadiusZ; cz <= worldRadiusZ; cz++) {
                for (int cy = 0; cy < worldHeightY; cy++) {

                    VoxelChunk chunk = level.loadChunk(cx, cy, cz);

                    level.generateTerrainForChunk(chunk);
                }
            }
        }

        level.generateAllMeshes();

        for (VoxelChunk chunk : level.getActiveChunks().values()) {
            chunk.update3DTexture();
        }

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
            renderLevel(level, mainShader, camera, textureAtlas);

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

//    glEnable(GL_DEPTH_TEST);
//    glDepthMask(true);
//    glDepthFunc(GL_LESS);

    private void renderLevel(Level level, ShaderPipeline pipeline, Camera camera, Texture atlasTexture) {

        //glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        pipeline.bind();

        pipeline.setUniform("textureAtlas", 0); // Verknüpft sampler2D textureAtlas mit GL_TEXTURE0
        pipeline.setUniform("voxelTex3D", 1);

        if (atlasTexture != null) {
            glActiveTexture(GL_TEXTURE0);
            atlasTexture.bind(0);
            pipeline.setUniform("textureAtlas", 0);
        }


        if (camera != null) {
            pipeline.setUniform("view", camera.getView());
            pipeline.setUniform("projection", camera.getProjection());
        }

        // 2. Über alle Chunks iterieren
        for (VoxelChunk chunk : level.getActiveChunks().values()) {

            // Welt-Offset und Dimensionen des spezifischen Chunks übergeben
            org.joml.Vector3i cPos = chunk.getChunkPosition();
            org.joml.Vector3i dims = chunk.getDimensions();
            pipeline.setUniform("chunkWorldPos", new org.joml.Vector3f(cPos.x * dims.x, cPos.y * dims.y, cPos.z * dims.z));
            pipeline.setUniform("chunkDimensions", dims);

            // 3. Die einzigartige 3D-Textur dieses Chunks an Slot 1 binden
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, chunk.getTexture3DId());
            pipeline.setUniform("voxelTex3D", 1);

            // 4. Das Dummy-VAO binden und über glDrawArrays rendern (keine Indices nötig!)
            Mesh chunkMesh = chunk.getMesh();
            if (chunkMesh != null) {
                glBindVertexArray(chunkMesh.vao());
                // Wir zeichnen rein über die im Shader berechnete Vertex-Anzahl!
                glDrawArrays(GL_TRIANGLES, 0, chunkMesh.vertexCount());
                glBindVertexArray(0);
            }
        }
    }
}
