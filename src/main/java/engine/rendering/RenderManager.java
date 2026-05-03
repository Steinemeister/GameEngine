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

        Scene scene = new Scene(pointLights, sceneObjects);

        Camera camera = new Camera();

        InputManager input = new InputManager(window);

        lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            double thisFrameTime = glfwGetTime();
            deltaTime = (float) (thisFrameTime - lastFrameTime);
            lastFrameTime = thisFrameTime;

            camera.handleInput(input, deltaTime);
            camera.update(aspectRatio);

            renderShadowMaps(scene, shadowShader);

            glViewport(0, 0, width, height);

            bindShadowMaps(scene.lights, mainShader);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderScene(scene, mainShader, camera);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void bindShadowMaps(List<PointLight> lights, ShaderPipeline mainPipeline) {
        mainPipeline.bind();

        for (int i = 0; i < lights.size(); i++) {
            PointLight light = lights.get(i);

            glActiveTexture(GL_TEXTURE0 + i);

            glBindTexture(GL_TEXTURE_CUBE_MAP, light.fbo.depthCubemap);

            mainPipeline.setUniform("lights[" + i + "].shadowMap", i);

            mainPipeline.setUniform("lights[" + i + "].position", light.position);
            mainPipeline.setUniform("lights[" + i + "].color", light.color.mul(light.intensity, new Vector3f()));
        }
    }

    private void renderScene(Scene scene, ShaderPipeline pipeline, Camera camera) {
        pipeline.bind();

        if (camera != null) {
            pipeline.setUniform("view", camera.getView());
            pipeline.setUniform("projection", camera.getProjection());
            pipeline.setUniform("far_plane", Constants.ZFar);
        }

        for (Object obj : scene.objects) {
            Matrix4f modelMatrix = obj.getModelMatrix();

            pipeline.setUniform("model", modelMatrix);

            Mesh mesh = obj.getMesh();
            glBindVertexArray(mesh.vao());

            glDrawElements(GL_TRIANGLES, mesh.vertexCount(), GL_UNSIGNED_INT, 0);

            glBindVertexArray(0);
        }
    }

    private void renderShadowMaps(Scene scene, ShaderPipeline shadowPipeline) {
        List<PointLight> lights = scene.lights;

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

            renderScene(scene, shadowPipeline, null);

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    private Matrix4f[] calculateShadowTransforms(Vector3f lightPos) {
        float aspect = 1.0f;
        float near = 1.0f;
        float far = 100.0f;
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(90.0f), aspect, near, far);

        return new Matrix4f[] {
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add( 1,  0,  0), new Vector3f(0, -1,  0)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add(-1,  0,  0), new Vector3f(0, -1,  0)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add( 0,  1,  0), new Vector3f(0,  0,  1)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add( 0, -1,  0), new Vector3f(0,  0, -1)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add( 0,  0,  1), new Vector3f(0, -1,  0)),
                new Matrix4f(proj).lookAt(lightPos, new Vector3f(lightPos).add( 0,  0, -1), new Vector3f(0, -1,  0))
        };
    }
}