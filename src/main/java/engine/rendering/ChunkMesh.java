package engine.rendering;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;

public class ChunkMesh {
    private final int vaoId;
    private final int vertexVboId;
    private final int vertexCount;

    public ChunkMesh(int vaoId, int vertexVboId, int vertexCount) {
        this.vaoId = vaoId;
        this.vertexVboId = vertexVboId;
        this.vertexCount = vertexCount;
    }

    public int getVaoId() { return vaoId; }
    public int getVertexCount() { return vertexCount; }

    /**
     * Löscht die Buffer aus dem Grafikspeicher.
     * DARF NUR IM RENDER-THREAD LAUFEN!
     */
    public void cleanup() {
        glDisableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vertexVboId);
        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }
}
