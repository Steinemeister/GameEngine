package engine.rendering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private final int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    public ShaderProgram(String vertexCode, String fragmentCode) {
        programId = glCreateProgram();
        if (programId == 0) {
            throw new IllegalStateException("Konnte Shader-Programm nicht erstellen.");
        }

        createVertexShader(vertexCode);
        createFragmentShader(fragmentCode);
        link();
    }

    /** Alternativer Konstruktor zum Laden aus Dateien */
    public static ShaderProgram fromFiles(String vertPath, String fragPath) throws IOException {
        String vertCode = Files.readString(Path.of(vertPath));
        String fragCode = Files.readString(Path.of(fragPath));
        return new ShaderProgram(vertCode, fragCode);
    }

    private void createVertexShader(String shaderCode) {
        vertexShaderId = createShader(shaderCode, GL_VERTEX_SHADER);
    }

    private void createFragmentShader(String shaderCode) {
        fragmentShaderId = createShader(shaderCode, GL_FRAGMENT_SHADER);
    }

    private int createShader(String shaderCode, int shaderType) {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) throw new IllegalStateException("Fehler beim Shader-Erstellen Typ: " + shaderType);
        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Fehler beim Kompilieren: " + glGetShaderInfoLog(shaderId, 1024));
        }
        glAttachShader(programId, shaderId);
        return shaderId;
    }

    private void link() {
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Fehler beim Linken: " + glGetProgramInfoLog(programId, 1024));
        }
        if (vertexShaderId != 0) glDetachShader(programId, vertexShaderId);
        if (fragmentShaderId != 0) glDetachShader(programId, fragmentShaderId);
        glValidateProgram(programId);
    }

    public void bind() { glUseProgram(programId); }
    public void unbind() { glUseProgram(0); }

    public void cleanup() {
        unbind();
        if (programId != 0) glDeleteProgram(programId);
    }

    public void setUniform(String uniformName, org.joml.Matrix4f matrix) {
        int location = glGetUniformLocation(programId, uniformName);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            glUniformMatrix4fv(location, false, matrix.get(stack.mallocFloat(16)));
        }
    }
}
