package engine.util;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

public class PointShadowBuffer {
    private final int fboId;
    private final int depthCubemap;
    private final int shadowSize = 1024; // Auflösung pro Cubemap-Seite

    public PointShadowBuffer() {
        fboId = glGenFramebuffers();
        depthCubemap = glGenTextures();

        glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubemap);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_DEPTH_COMPONENT,
                    shadowSize, shadowSize, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer)null);
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthCubemap, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new RuntimeException("Shadow FBO incomplete");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public Matrix4f[] getShadowMatrices(Vector3f lightPos) {
        float far = 25.0f; // Muss mit 'far_plane' im Shader übereinstimmen
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(90.0f), 1.0f, 1.0f, far);

        return new Matrix4f[] {
                new Matrix4f(projection).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(1, 0, 0), new Vector3f(0, -1, 0))),
                new Matrix4f(projection).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(-1, 0, 0), new Vector3f(0, -1, 0))),
                new Matrix4f(projection).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0, 1, 0), new Vector3f(0, 0, 1))),
                new Matrix4f(projection).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0, -1, 0), new Vector3f(0, 0, -1))),
                new Matrix4f(projection).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0, 0, 1), new Vector3f(0, -1, 0))),
                new Matrix4f(projection).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0, 0, -1), new Vector3f(0, -1, 0)))
        };
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, shadowSize, shadowSize);
    }

    public void unbind(int windowWidth, int windowHeight) {
        // Bindet den Standard-Framebuffer (Bildschirm)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Setzt den Viewport zurück auf die Fenstermaße
        glViewport(0, 0, windowWidth, windowHeight);
    }

    public int getDepthCubemap() {
        return this.depthCubemap;
    }
}
