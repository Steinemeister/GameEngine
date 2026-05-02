package engine.util;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;

public class Camera {
    private Matrix4f projection = new Matrix4f();
    private Matrix4f view = new Matrix4f();
    private FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public void update(int width, int height) {
        // Perspektive: 45 Grad Sichtfeld, Seitenverhältnis, Z-Near, Z-Far
        projection.setPerspective((float) Math.toRadians(45.0f), (float)width/height, 0.1f, 100.0f);

        // Kamera: Position (0, 2, 5), Zielpunkt (0, 0, 0), Up-Vektor (0, 1, 0)
        view.identity().lookAt(0.0f, 2.0f, 5.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
    }

    public void upload(int shaderProgram) {
        int projLoc = glGetUniformLocation(shaderProgram, "projection");
        int viewLoc = glGetUniformLocation(shaderProgram, "view");

        glUniformMatrix4fv(projLoc, false, projection.get(matrixBuffer));
        glUniformMatrix4fv(viewLoc, false, view.get(matrixBuffer));
    }
}
