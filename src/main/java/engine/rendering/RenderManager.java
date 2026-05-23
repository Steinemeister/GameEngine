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
    private int width = 640;
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

        // Generiert den 1x1 Pixel Atlas einfriersicher
        TextureAtlasGenerator.generateAtlas(1);

        ShaderPipeline mainShader = new ShaderPipeline(
                "src/main/resources/shaders/main_v.glsl",
                "src/main/resources/shaders/main_f.glsl"
        );

        Texture textureAtlas = new Texture("src/main/generated/textureAtlas.png");

        // Ein einfaches statisches Punktlicht zur Ausleuchtung der Hügel
        List<PointLight> pointLights = new ArrayList<>();
        pointLights.add(new PointLight(
                new Vector3f(8.0f, 50.0f, 8.0f),
                new Vector3f(1.0f, 1.0f, 1.0f),
                1.0f
        ));

        Level level = new Level(new Vector3i(16, 16, 16));

        // Ausdehnung der generierten Welt beim Start
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

            // Licht sanft kreisen lassen für dynamische Helligkeitswechsel auf den Blöcken
            pointLights.get(0).position.rotateAxis((float) Math.toRadians(deltaTime * 15), 0.0f, 1.0f, 0.0f);

            camera.handleInput(input, deltaTime);
            camera.update(aspectRatio);

            // Nur noch der Haupt-Renderpass, direkt auf das Fenster
            glViewport(0, 0, width, height);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(GL_LESS);

            // Level mit dem kreisenden Licht auf den Bildschirm zeichnen
            renderLevel(level, mainShader, camera, textureAtlas, pointLights.getFirst().position);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        logger.info("stopping rendering loop");
        level.cleanup();
    }

    private void renderLevel(Level level, ShaderPipeline pipeline, Camera camera, Texture atlasTexture, Vector3f lightPosition) {
        // 1. Haupt-Shader aktivieren
        pipeline.bind();

        // 2. Uniforms für die Textur-Einheiten im Shader fest verdrahten
        pipeline.setUniform("textureAtlas", 0);
        pipeline.setUniform("voxelTex3D", 1);

        // 3. Den 2D-Texturatlas an Textureinheit 0 binden
        glActiveTexture(GL_TEXTURE0);
        if (atlasTexture != null) {
            atlasTexture.bind(0);
        } else {
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // 4. Kamera-Matrizen und Lichtposition übertragen
        if (camera != null) {
            pipeline.setUniform("view", camera.getView());
            pipeline.setUniform("projection", camera.getProjection());
            pipeline.setUniform("far_plane", Constants.ZFar);
            pipeline.setUniform("lightPos", lightPosition);
        }

        // 5. Model-Matrix setzen (Identitätsmatrix, da Weltkoordinaten im Mesh stecken)
        org.joml.Matrix4f identityMatrix = new org.joml.Matrix4f();
        pipeline.setUniform("model", identityMatrix);

        // 6. Über alle geladenen Chunks iterieren und prozedural rendern
        for (VoxelChunk chunk : level.getActiveChunks().values()) {
            org.joml.Vector3i cPos = chunk.getChunkPosition();
            org.joml.Vector3i dims = chunk.getDimensions();

            // Chunk-spezifische Positionsdaten an den Shader senden
            pipeline.setUniform("chunkWorldPos", new org.joml.Vector3f(cPos.x * dims.x, cPos.y * dims.y, cPos.z * dims.z));
            pipeline.setUniform("chunkDimensions", dims);

            // Die 3D-Textur dieses Chunks an Textureinheit 1 binden
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, chunk.getTexture3DId());

            // Das Dummy-VAO binden und via glDrawArrays die Vertices simulieren
            Mesh chunkMesh = chunk.getMesh();
            if (chunkMesh != null && chunkMesh.vertexCount() > 0) {
                glBindVertexArray(chunkMesh.vao());
                glDrawArrays(GL_TRIANGLES, 0, chunkMesh.vertexCount());
                glBindVertexArray(0);
            }
        }

        // Sauberes OpenGL-State-Management: Zurück auf Standard-Einheit 0
        glActiveTexture(GL_TEXTURE0);
    }
}
