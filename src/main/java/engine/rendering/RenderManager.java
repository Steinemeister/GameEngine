package engine.rendering;

import engine.object.Mesh;
import engine.object.Texture;
import engine.util.*;
import engine.world.Level;
import engine.world.VoxelChunk;
import logger.Logger;
import logger.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL40.*;

public class RenderManager implements Runnable {
    private Logger logger;

    private long window;
    private int width = 640;
    private int height = 480;
    private float aspectRatio;

    private double lastFrameTime = 0.0;
    private float deltaTime = 1.0f;

    private float dayTimeAngle = (float) (0 - Math.PI);

    private double fpsTimer = 0.0;
    private int fpsCounter = 0;

    private int visibleChunksCount = 0;



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

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Voxel Level Engine", 0L, 0L);
        if (window == 0L) {
            logger.error("unable to create window");
            throw new RuntimeException("unable to create window");
        }

        glfwSetWindowSizeLimits(window, 640, 480, GLFW_DONT_CARE, GLFW_DONT_CARE);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // V-Sync off
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
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void loop() {
        logger.info("starting rendering loop");

        // Generiert den 1x1 Pixel Atlas einfriersicher
        TextureAtlasGenerator.generateAtlas(1);

        ShaderPipeline depthShader = new ShaderPipeline(
                "src/main/resources/shaders/main_v.glsl",
                "src/main/resources/shaders/depth_f.glsl"
        );

        ShaderPipeline mainShader = new ShaderPipeline(
                "src/main/resources/shaders/main_v.glsl",
                "src/main/resources/shaders/main_f.glsl"
        );

        ShaderPipeline skyboxShader = new ShaderPipeline(
                "src/main/resources/shaders/skybox_v.glsl",
                "src/main/resources/shaders/skybox_f.glsl"
        );

        ShaderPipeline transparentShader = new ShaderPipeline(
                "src/main/resources/shaders/transparent_v.glsl",
                "src/main/resources/shaders/transparent_f.glsl");


        Texture textureAtlas = new Texture("src/main/generated/textureAtlas.png");

        org.joml.Vector3f sunDirection = new org.joml.Vector3f(-0.6f, -0.8f, -0.4f).normalize();
        org.joml.Vector3f sunColor = new org.joml.Vector3f(1.0f, 1.0f, 0.9f);

        Level level = new Level(new Vector3i(16, 16, 16));
        Camera camera = new Camera();

        camera.setPos(new Vector3f(0, 90, 0));

        int viewRadius = 30;
        level.updateVisibleChunks(camera.getPosition(), viewRadius);

        InputManager input = new InputManager(window);
        lastFrameTime = glfwGetTime();
        fpsTimer = lastFrameTime;



        int frameCounter = 0;

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            double thisFrameTime = glfwGetTime();
            deltaTime = (float) (thisFrameTime - lastFrameTime);
            lastFrameTime = thisFrameTime;

            dayTimeAngle += deltaTime * 0.01f;
            if (dayTimeAngle > Math.PI * 2) {
                dayTimeAngle -= (float) (Math.PI * 2);
            }

            sunDirection.set(
                    -0.6f, // Leichte Neigung auf der X-Achse für einen schönen Schattenwinkel
                    (float) Math.sin(dayTimeAngle), // Y-Höhe (Tag positiv, Nacht negativ)
                    (float) Math.cos(dayTimeAngle)  // Z-Achsen-Verschiebung
            ).normalize();

            float auroraNoise = (float) Math.cos(dayTimeAngle * 0.15f) * 0.6f + 0.4f;
            float auroraActivity = Math.max(0.0f, Math.min(1.0f, auroraNoise));

            // Wenn die Aktivität unter einen Schwellenwert fällt, bleibt es komplett dunkel
            if (auroraActivity < 0.25f) {
                auroraActivity = 0.0f;
            } else {
                // Skaliere den Rest sauber auf 0.0 bis 1.0
                auroraActivity = (auroraActivity - 0.25f) / 0.75f;
            }

            fpsCounter++;

            if (thisFrameTime - fpsTimer >= 1.0) {
                String title = getTitle(level);

                // Fenstertitel setzen
                glfwSetWindowTitle(window, title);

                // Zähler zurücksetzen
                fpsCounter = 0;
                fpsTimer = thisFrameTime;
            }

            input.updateMouse();

            input.handleModeSwitch();

            input.updateCameraMovement(camera, level, deltaTime);

            camera.update(aspectRatio);

            // 1. Chunks um die Kamera herum im Hintergrund anfordern
            frameCounter++;
            if (frameCounter >= 20) {
                level.updateVisibleChunks(camera.getPosition(), viewRadius);
                frameCounter = 0;
            }


            // 2. NEU: Fertig berechnete Hintergrund-Chunks stoßfrei auf die GPU laden
            level.uploadPendingTextures(camera.getPosition());

            glViewport(0, 0, width, height);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(GL_LESS);

            // 3. Zeichnen (Nutzt jetzt das blitzschnelle Frustum Culling)
            renderLevel(level, mainShader,skyboxShader, transparentShader,depthShader, camera,
                    textureAtlas, sunDirection, sunColor, auroraActivity);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        logger.info("stopping rendering loop");
        level.cleanup();
    }

    @NotNull
    private String getTitle(Level level) {
        long activeChunks = level.getActiveChunks().size();
        long airChunks = level.getActiveChunks().values().stream()
                .filter(VoxelChunk::isFullyOccluded).count();
        long chunksNonAir = activeChunks - airChunks;


        // Formatierten String für den Titel bauen
        return String.format(
                "Voxel Level Engine | " +
                        "FPS: %d | " +
                        "Chunks: %d | " +
                        "Chunks with blocks: %d | " +
                        "Chunks in View-frustum %d",
                fpsCounter,
                activeChunks,
                chunksNonAir,
                this.visibleChunksCount
        );
    }

    private void renderLevel(Level level, ShaderPipeline mainPipeline,ShaderPipeline skyPipeline,
                             ShaderPipeline transparentPipeline, ShaderPipeline depthShader,
                             Camera camera, Texture atlasTexture, Vector3f sunDir, Vector3f sunColor, float auroraActivity) {

        this.visibleChunksCount = 0;

        org.joml.Matrix4f identityMatrix = new org.joml.Matrix4f();
        org.joml.Matrix4f vpMatrix = new org.joml.Matrix4f(camera.getProjection()).mul(camera.getView());
        FrustumIntersection frustum = new FrustumIntersection(vpMatrix);
        Vector3i dims = level.getChunkDimensions();

        // Textur-Slot 0 aktivieren und Atlas binden
        glActiveTexture(GL_TEXTURE0);
        if (atlasTexture != null) {
            atlasTexture.bind(0);
        }

        // =================================================================
        // DURCHGANG 1: Z-PREPASS (Nur Tiefe berechnen, keine Farben)
        // =================================================================
        depthShader.bind();
        // Übergreife das Standard-Setup für Matrizen (keine Licht-Uniforms nötig!)
        setupCommonUniforms(depthShader, camera,sunDir, sunColor, identityMatrix);

        glDepthMask(true);                    // Tiefenpuffer-Schreiben erlauben
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LESS);                 // Standard-Tiefenfunktion

        // FÄRBUNG DEAKTIVIEREN: Die GPU sperrt die Ausgabe in den Bildpuffer
        glColorMask(false, false, false, false);

        // Zeichne alle sichtbaren soliden Chunks (ohne zu zählen, da 1b identisch ist)
        renderAllVisibleChunks(level, depthShader, frustum, dims, false);


        // =================================================================
        // DURCHGANG 2: DIE SOLIDEN BLÖCKE (Main Shader Pipeline)
        // =================================================================
        mainPipeline.bind();
        setupCommonUniforms(mainPipeline, camera, sunDir, sunColor, identityMatrix);

        glColorMask(true, true, true, true);

        glDepthMask(false);

        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);

        renderAllVisibleChunks(level, mainPipeline, frustum, dims, true);

        glDepthFunc(GL_LESS);
        glDepthMask(true);

        // =================================================================
        // DURCHGANG 3: FULLSCREEN SKYBOX
        // =================================================================
        skyPipeline.bind();

        // 1. WICHTIG: Die Translation (Position) aus der View-Matrix entfernen
        org.joml.Matrix4f skyView = new org.joml.Matrix4f(camera.getView());
        skyView.m30(0); // X-Translation nullen
        skyView.m31(0); // Y-Translation nullen
        skyView.m32(0); // Z-Translation nullen

        // Matrizen invertieren
        org.joml.Matrix4f invProjection = new org.joml.Matrix4f(camera.getProjection()).invert();
        org.joml.Matrix4f invView = skyView.invert();

        skyPipeline.setUniform("invProjection", invProjection);
        skyPipeline.setUniform("invView", invView);
        skyPipeline.setUniform("sunDirection", sunDir);
        skyPipeline.setUniform("sunColor", sunColor);
        skyPipeline.setUniform("time", (float) glfwGetTime());
        skyPipeline.setUniform("auroraActivity", auroraActivity);

        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        // 2. WICHTIG FÜR CORE PROFILE: Ein Dummy-VAO binden, sonst zeichnet OpenGL nichts!
        // Falls du kein globales leeres VAO hast, kannst du hier testweise die ID 0 binden
        // oder temporär ein temporäres nutzen. Am saubersten ist:
        int dummyVAO = org.lwjgl.opengl.GL30.glGenVertexArrays();
        org.lwjgl.opengl.GL30.glBindVertexArray(dummyVAO);

        // Zeichnen
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        // Aufräumen
        org.lwjgl.opengl.GL30.glBindVertexArray(0);
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(dummyVAO); // In der Praxis einmalig in init() erzeugen!

        glDepthFunc(GL_LESS);

        // =================================================================
        // DURCHGANG 4: DAS TRANSPARENTE WASSER (Transparent Shader Pipeline)
        // =================================================================
        transparentPipeline.bind();
        setupCommonUniforms(transparentPipeline, camera, sunDir, sunColor, identityMatrix);

        transparentPipeline.setUniform("time", (float) glfwGetTime());

        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        renderAllVisibleChunks(level, transparentPipeline, frustum, dims, false);

        // =================================================================
        // OPENGL STATE ZURÜCKSETZEN
        // =================================================================
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glActiveTexture(GL_TEXTURE0);
    }

    private void setupCommonUniforms(ShaderPipeline pipeline, Camera camera, Vector3f sunDir, Vector3f sunColor, org.joml.Matrix4f modelMatrix) {
        pipeline.setUniform("textureAtlas", 0);
        pipeline.setUniform("voxelTex3D", 1);
        pipeline.setUniform("model", modelMatrix);

        if (camera != null) {
            pipeline.setUniform("view", camera.getView());
            pipeline.setUniform("projection", camera.getProjection());
            pipeline.setUniform("far_plane", Constants.ZFar);
            pipeline.setUniform("sunDirection", sunDir);
            pipeline.setUniform("sunColor", sunColor);
        }
    }

    private void renderAllVisibleChunks(Level level, ShaderPipeline pipeline,
                                        FrustumIntersection frustum, Vector3i dims, boolean count) {
        for (VoxelChunk chunk : level.getActiveChunks().values()) {
            if (chunk.isFullyOccluded()) continue;

            Mesh chunkMesh = chunk.getMesh();
            if (chunkMesh == null || chunkMesh.vertexCount() == 0) continue;

            Vector3i cPos = chunk.getChunkPosition();
            float minX = cPos.x * dims.x;
            float minY = cPos.y * dims.y;
            float minZ = cPos.z * dims.z;

            if (frustum.intersectAab(minX, minY, minZ, minX + dims.x, minY + dims.y, minZ + dims.z) == FrustumIntersection.OUTSIDE) {
                continue;
            }

            if (count) visibleChunksCount++;

            pipeline.setUniform("chunkWorldPos", new org.joml.Vector3f(minX, minY, minZ));
            pipeline.setUniform("chunkDimensions", dims);

            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_3D, chunk.getTexture3DId());

            glBindVertexArray(chunkMesh.vao());

            glDrawArrays(GL_TRIANGLES, 0, chunkMesh.vertexCount());


            glBindVertexArray(0);
        }
    }
}
