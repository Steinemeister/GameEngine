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
import org.joml.FrustumIntersection;
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
        Camera camera = new Camera();

        // NEU: Initialer Aufruf mit der Startposition der Kamera und Sichtradius (z.B. 4 Chunks weit)
        int viewRadius = 12;
        level.updateVisibleChunks(camera.getPosition(), viewRadius);

        InputManager input = new InputManager(window);
        lastFrameTime = glfwGetTime();

        int frameCounter = 0;

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            double thisFrameTime = glfwGetTime();
            deltaTime = (float) (thisFrameTime - lastFrameTime);
            lastFrameTime = thisFrameTime;

            pointLights.get(0).position.rotateAxis((float) Math.toRadians(deltaTime * 15), 0.0f, 1.0f, 0.0f);

            camera.handleInput(input, deltaTime);
            camera.update(aspectRatio);

            // 1. Chunks um die Kamera herum im Hintergrund anfordern
            frameCounter++;
            if (frameCounter >= 10) {
                level.updateVisibleChunks(camera.getPosition(), viewRadius);
            }


            // 2. NEU: Fertig berechnete Hintergrund-Chunks stoßfrei auf die GPU laden
            level.uploadPendingTextures(camera.getPosition());

            glViewport(0, 0, width, height);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(GL_LESS);

            // 3. Zeichnen (Nutzt jetzt das blitzschnelle Frustum Culling)
            renderLevel(level, mainShader, camera, textureAtlas, pointLights.get(0).position);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        logger.info("stopping rendering loop");
        level.cleanup();
    }

    private void renderLevel(Level level, ShaderPipeline pipeline, Camera camera, Texture atlasTexture, Vector3f lightPosition) {
        pipeline.bind();

        pipeline.setUniform("textureAtlas", 0);
        pipeline.setUniform("voxelTex3D", 1);

        glActiveTexture(GL_TEXTURE0);
        if (atlasTexture != null) {
            atlasTexture.bind(0);
        }

        if (camera != null) {
            pipeline.setUniform("view", camera.getView());
            pipeline.setUniform("projection", camera.getProjection());
            pipeline.setUniform("far_plane", Constants.ZFar);
            pipeline.setUniform("lightPos", lightPosition);
        }

        org.joml.Matrix4f identityMatrix = new org.joml.Matrix4f();
        pipeline.setUniform("model", identityMatrix);

        // --- NEU: Sichtkegel (Frustum) aus der Kamera-Kombination berechnen ---
        org.joml.Matrix4f vpMatrix = new org.joml.Matrix4f(camera.getProjection()).mul(camera.getView());
        FrustumIntersection frustum = new FrustumIntersection(vpMatrix);

        Vector3i dims = level.getChunkDimensions();

        for (VoxelChunk chunk : level.getActiveChunks().values()) {

            if (chunk.isFullyOccluded()) {
                continue;
            }
            // Nur rendern, wenn der Chunk fertig generiert ist
            Mesh chunkMesh = chunk.getMesh();
            if (chunkMesh == null || chunkMesh.vertexCount() == 0) continue;

            // Welt-Position des Chunks für den Sichtbarkeitstest bestimmen
            Vector3i cPos = chunk.getChunkPosition();
            float minX = cPos.x * dims.x;
            float minY = cPos.y * dims.y;
            float minZ = cPos.z * dims.z;
            float maxX = minX + dims.x;
            float maxY = minY + dims.y;
            float maxZ = minZ + dims.z;

            // FRUSTUM CULLING: Wenn der Chunk komplett außerhalb des Sichtfelds ist -> Überspringen!
            if (frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ) == FrustumIntersection.OUTSIDE) {
                continue;
            }

            // Chunk-spezifische Uniforms setzen und zeichnen
            pipeline.setUniform("chunkWorldPos", new org.joml.Vector3f(minX, minY, minZ));
            pipeline.setUniform("chunkDimensions", dims);

            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, chunk.getTexture3DId());

            glBindVertexArray(chunkMesh.vao());
            glDrawArrays(GL_TRIANGLES, 0, chunkMesh.vertexCount());
            glBindVertexArray(0);
        }
        glActiveTexture(GL_TEXTURE0);
    }
}
