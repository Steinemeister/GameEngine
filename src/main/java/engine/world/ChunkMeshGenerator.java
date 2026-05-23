package engine.world;

import engine.object.Mesh;

import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public class ChunkMeshGenerator {
    public static Mesh generateMesh(VoxelChunk chunk, Level level) {
        int totalVoxels = chunk.getDimensions().x * chunk.getDimensions().y * chunk.getDimensions().z;
        int maxVertices = totalVoxels * 6 * 6; // Wichtig: 36 Durchläufe pro Voxel!

        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        // Da wir prozedural arbeiten, binden wir KEINE VBOs oder EBOs!
        glBindVertexArray(0);

        // Wir übergeben maxVertices als vertexCount in den Mesh-Record
        return new Mesh(vao, 0, 0, maxVertices);
    }
}
