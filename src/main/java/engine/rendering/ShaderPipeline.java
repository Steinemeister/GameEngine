package engine.rendering;

import engine.lighting.PointLight;
import logger.Logger;
import logger.LoggerFactory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL40.GL_TESS_CONTROL_SHADER;
import static org.lwjgl.opengl.GL40.GL_TESS_EVALUATION_SHADER;

public class ShaderPipeline {
    private final Logger logger = LoggerFactory.getLogger("shaderPipeline");
    private final int programId;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public ShaderPipeline(String vertexPath, String fragmentPath) {
        this(vertexPath, fragmentPath, null, null, null);
    }

    public ShaderPipeline(String vertexPath, String fragmentPath, String geometryPath, String tessControlPath, String tessEvalPath) {
        programId = glCreateProgram();
        List<Integer> shaderIds = new ArrayList<>();

        shaderIds.add(compileAndAttach(vertexPath, GL_VERTEX_SHADER));
        shaderIds.add(compileAndAttach(fragmentPath, GL_FRAGMENT_SHADER));

        if (geometryPath != null)    shaderIds.add(compileAndAttach(geometryPath, GL_GEOMETRY_SHADER));
        if (tessControlPath != null && tessEvalPath != null) {
            shaderIds.add(compileAndAttach(tessControlPath, GL_TESS_CONTROL_SHADER));
            shaderIds.add(compileAndAttach(tessEvalPath, GL_TESS_EVALUATION_SHADER));
        }

        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            logger.error(new RuntimeException("error linkin shader: " + glGetProgramInfoLog(programId)));
        }

        for (int id : shaderIds) {
            glDetachShader(programId, id);
            glDeleteShader(id);
        }
    }

    private int compileAndAttach(String path, int type) {
        try {
            String source = Files.readString(Paths.get(path));
            int shaderId = glCreateShader(type);
            glShaderSource(shaderId, source);
            glCompileShader(shaderId);

            if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
                logger.error(new RuntimeException("compiler error while compiling: " + path + ": " + glGetShaderInfoLog(shaderId)));
            }

            glAttachShader(programId, shaderId);
            return shaderId;
        } catch (Exception e) {
            logger.error(new RuntimeException("unable to load shader: " + path, e));
            return  0;
        }
    }

    private int getUniformLocation(String name) {
        return uniforms.computeIfAbsent(name, k -> {
            int loc = glGetUniformLocation(programId, k);
            if (loc == -1) System.err.println("Warnung: Uniform '" + k + "' nicht gefunden!");
            return loc;
        });
    }

    public void setUniform(String name, Matrix4f value) {
        glUniformMatrix4fv(getUniformLocation(name), false, value.get(matrixBuffer));
    }

    public void setUniform(String name, Vector3f value) {
        glUniform3f(getUniformLocation(name), value.x, value.y, value.z);
    }

    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    public void setUniform(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    public void setUniform(String name, Matrix4f[] matrices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(matrices.length * 16);
            for (Matrix4f m : matrices) m.get(buffer); // Schreibt Matrizen nacheinander in den Buffer
            buffer.flip();
            glUniformMatrix4fv(getUniformLocation(name), false, buffer);
        }
    }

    public void setLights(List<PointLight> lights) {
        int count = Math.min(lights.size(), 10); // MAX_LIGHTS (hier 10)
        setUniform("activeLightCount", count);

        for (int i = 0; i < count; i++) {
            PointLight light = lights.get(i);
            setUniform("lights[" + i + "].position", light.position);
            setUniform("lights[" + i + "].color", light.color);
            setUniform("lights[" + i + "].intensity", light.intensity);
        }
    }

    public void bind() {
        glUseProgram(programId);
    }
    public void unbind() {
        glUseProgram(0);
    }
    public void cleanup() {
        glDeleteProgram(programId);
    }
}