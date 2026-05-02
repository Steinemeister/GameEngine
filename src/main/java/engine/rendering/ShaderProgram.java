package engine.rendering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    public final int programId;

    public ShaderProgram(String vertexPath, String fragmentPath) {
        // 1. Shader laden und kompilieren
        int vertexShader = compileShader(vertexPath, GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentPath, GL_FRAGMENT_SHADER);

        // 2. Programm erstellen und Shader verlinken
        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader Verlinkung fehlgeschlagen: " + glGetProgramInfoLog(programId));
        }

        // 3. Shader können nach dem Verlinken gelöscht werden
        glDetachShader(programId, vertexShader);
        glDetachShader(programId, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    private int compileShader(String path, int type) {
        try {
            String source = Files.readString(Paths.get(path));
            int shaderId = glCreateShader(type);
            glShaderSource(shaderId, source);
            glCompileShader(shaderId);

            if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new RuntimeException("Fehler beim Kompilieren von " + path + ": " + glGetShaderInfoLog(shaderId));
            }
            return shaderId;
        } catch (IOException e) {
            throw new RuntimeException("Shader Datei konnte nicht gelesen werden: " + path, e);
        }
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public int getUniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) glDeleteProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }
}
