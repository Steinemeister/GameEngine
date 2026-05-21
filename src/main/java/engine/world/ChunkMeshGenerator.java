package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_FLOAT;
import static org.lwjgl.opengl.GL30.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL30.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glBufferData;
import static org.lwjgl.opengl.GL30.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glGenBuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glVertexAttribPointer;

public class ChunkMeshGenerator {
    private static final int[][] DIRECTIONS = {
            { 0,  0,  1}, // Vorne
            { 0,  0, -1}, // Hinten
            {-1,  0,  0}, // Links
            { 1,  0,  0}, // Rechts
            { 0,  1,  0}, // Oben
            { 0, -1,  0}  // Unten
    };

    private static final float[][] VERTICES = {
            {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, // Vorne
            {1, 0, -1}, {0, 0, -1}, {0, 1, -1}, {1, 1, -1}, // Hinten
            {0, 0, -1}, {0, 0, 1}, {0, 1, 1}, {0, 1, -1}, // Links
            {1, 0, 1}, {1, 0, -1}, {1, 1, -1}, {1, 1, 1}, // Rechts
            {0, 1, 1}, {1, 1, 1}, {1, 1, -1}, {0, 1, -1}, // Oben
            {0, 0, -1}, {1, 0, -1}, {1, 0, 1}, {0, 0, 1}  // Unten
    };

    private static final float[][] NORMALS = {
            { 0.0f,  0.0f,  1.0f}, // Vorne
            { 0.0f,  0.0f, -1.0f}, // Hinten
            {-1.0f,  0.0f,  0.0f}, // Links
            { 1.0f,  0.0f,  0.0f}, // Rechts
            { 0.0f,  1.0f,  0.0f}, // Oben
            { 0.0f, -1.0f,  0.0f}  // Unten
    };

    private static final float[][] UVs = {
            {0.0f, 0.0f}, {1.0f, 0.0f}, {1.0f, 1.0f}, {0.0f, 1.0f}
    };

    private static final int[] FACE_INDICES = { 0, 1, 2, 2, 3, 0 };

    /**
     * Generiert das Mesh und prüft Block-Nachbarn auch in angrenzenden Chunks des Levels.
     */
    public static Mesh generateMesh(VoxelChunk chunk, Level level) {
        List<float[]> finalVertices = new ArrayList<>();
        List<Integer> finalIndices = new ArrayList<>();
        int vertexIndexOffset = 0;

        Vector3i dims = chunk.getDimensions();
        Vector3i chunkPos = chunk.getChunkPosition();

        float worldXOffset = chunkPos.x * dims.x;
        float worldYOffset = chunkPos.y * dims.y;
        float worldZOffset = chunkPos.z * dims.z;

        // Temporäre Vektoren für die Weltkoordinaten-Abfrage im Level
        Vector3f neighborWorldPos = new Vector3f();

        for (int x = 0; x < dims.x; x++) {
            for (int y = 0; y < dims.y; y++) {
                for (int z = 0; z < dims.z; z++) {

                    if (!chunk.getVoxel(x, y, z)) continue;

                    for (int face = 0; face < 6; face++) {
                        int nx = x + DIRECTIONS[face][0];
                        int ny = y + DIRECTIONS[face][1];
                        int nz = z + DIRECTIONS[face][2];

                        boolean shouldRenderFace = false;

                        // Wenn der Nachbar im selben Chunk liegt
                        if (chunk.isLocalCoordinateInBounds(nx, ny, nz)) {
                            if (!chunk.getVoxel(nx, ny, nz)) {
                                shouldRenderFace = true; // Luftblock im eigenen Chunk
                            }
                        } else {
                            // Nachbar liegt in einem anderen Chunk -> Über das Level prüfen
                            neighborWorldPos.set(worldXOffset + nx, worldYOffset + ny, worldZOffset + nz);
                            if (!level.getVoxelAtWorld(neighborWorldPos)) {
                                shouldRenderFace = true; // Luftblock oder ungeladener Chunk
                            }
                        }

                        if (shouldRenderFace) {
                            for (int v = 0; v < 4; v++) {
                                int vertIdx = face * 4 + v;

                                float posX = x + VERTICES[vertIdx][0] + worldXOffset;
                                float posY = y + VERTICES[vertIdx][1] + worldYOffset;
                                float posZ = z + VERTICES[vertIdx][2] + worldZOffset;

                                float texU = UVs[v][0];
                                float texV = UVs[v][1];

                                float normX = NORMALS[face][0];
                                float normY = NORMALS[face][1];
                                float normZ = NORMALS[face][2];

                                finalVertices.add(new float[]{ posX, posY, posZ, texU, texV, normX, normY, normZ });
                            }

                            for (int i = 0; i < 6; i++) {
                                finalIndices.add(vertexIndexOffset + FACE_INDICES[i]);
                            }

                            vertexIndexOffset += 4;
                        }
                    }
                }
            }
        }

        if (finalIndices.isEmpty()) {
            return new Mesh(0, 0, 0, 0);
        }

        return createMesh(finalVertices, finalIndices);
    }

    private static Mesh createMesh(List<float[]> vertices, List<Integer> indices) {
        FloatBuffer vBuffer = MemoryUtil.memAllocFloat(vertices.size() * 8);
        IntBuffer iBuffer = MemoryUtil.memAllocInt(indices.size());

        try {
            for (float[] v : vertices) vBuffer.put(v);
            vBuffer.flip();

            for (int i : indices) iBuffer.put(i);
            iBuffer.flip();

            int vao = glGenVertexArrays();
            int vbo = glGenBuffers();
            int ebo = glGenBuffers();

            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuffer, GL_STATIC_DRAW);

            int stride = 8 * 4;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 20);
            glEnableVertexAttribArray(2);

            glBindVertexArray(0);

            return new Mesh(vao, vbo, ebo, indices.size());
        } finally {
            MemoryUtil.memFree(vBuffer);
            MemoryUtil.memFree(iBuffer);
        }
    }
}
