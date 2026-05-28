package engine.object;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;

public record Mesh(int vao, int vbo, int ebo, int vertexCount) {
    public void render() {
        if (vertexCount == 0 || vao == 0) return; // Sicherheit gegen leere Chunks

        glBindVertexArray(vao);

        // KORREKTUR: glDrawArrays statt glDrawElements!
        // Da wir die Dreiecke direkt über das VBO streamen (ohne EBO-Indizes).
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);

        glBindVertexArray(0);
    }

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (ebo != 0) glDeleteBuffers(ebo);
        if (vao != 0) glDeleteVertexArrays(vao);
    }
}
