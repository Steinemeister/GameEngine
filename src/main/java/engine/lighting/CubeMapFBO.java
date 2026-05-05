package engine.lighting;

import engine.util.Constants;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

public class CubeMapFBO {

    public int shadowMapSize = 4096;
    public int depthCubemap = glGenTextures();

    float aspect = 1.0f;
    float near = Constants.ZNear;
    float far = Constants.ZFar; // Dein far_plane Wert
    Matrix4f shadowProj = new Matrix4f().perspective((float) Math.toRadians(90.0f), aspect, near, far);

    Vector3f lightPos; // Beispielposition
    Matrix4f[] shadowTransforms = new Matrix4f[6];

    public CubeMapFBO(PointLight light) {

        lightPos = light.position;

        glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubemap);

        for (int i = 0; i < 6; i++) {
            // Erstellt die 6 Seiten der Cubemap
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_DEPTH_COMPONENT,
                    shadowMapSize, shadowMapSize, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        }

        // Textur-Parameter für Tiefenkarten
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);


        shadowTransforms[0] = new Matrix4f(shadowProj).lookAt(lightPos, new Vector3f(lightPos).add(1, 0, 0), new Vector3f(0, -1, 0));  // +X
        shadowTransforms[1] = new Matrix4f(shadowProj).lookAt(lightPos, new Vector3f(lightPos).add(-1, 0, 0), new Vector3f(0, -1, 0)); // -X
        shadowTransforms[2] = new Matrix4f(shadowProj).lookAt(lightPos, new Vector3f(lightPos).add(0, 1, 0), new Vector3f(0, 0, 1));   // +Y
        shadowTransforms[3] = new Matrix4f(shadowProj).lookAt(lightPos, new Vector3f(lightPos).add(0, -1, 0), new Vector3f(0, 0, -1)); // -Y
        shadowTransforms[4] = new Matrix4f(shadowProj).lookAt(lightPos, new Vector3f(lightPos).add(0, 0, 1), new Vector3f(0, -1, 0));  // +Z
        shadowTransforms[5] = new Matrix4f(shadowProj).lookAt(lightPos, new Vector3f(lightPos).add(0, 0, -1), new Vector3f(0, -1, 0)); // -Z
    }

    public int bind() {
        // FBO binden und Textur anhängen
        int shadowFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFBO);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthCubemap, 0);
        glDrawBuffer(GL_NONE); // Kein Farbpuffer nötig
        glReadBuffer(GL_NONE);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer nicht vollständig!");
        }
        return shadowFBO;
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

}
