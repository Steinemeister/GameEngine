package engine.world;

import engine.object.Mesh;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

public class ChunkMeshGenerator {
    public record ChunkMeshResult(Mesh solidMesh, Mesh waterMesh) {}

    public static ChunkMeshResult generateMeshes(VoxelChunk chunk, Level level) {
        Vector3i dims = chunk.getDimensions();
        Vector3i chunkPos = chunk.getChunkPosition();

        // ZWEI separate Listen für solide Blöcke und Wasser
        List<Integer> solidVertices = new ArrayList<>();
        List<Integer> waterVertices = new ArrayList<>();

        int[][][] faceVertices = {
                { {1,0,0}, {0,0,0}, {0,1,0}, {1,0,0}, {0,1,0}, {1,1,0} }, // 0: Hinten (Z-)
                { {0,0,1}, {1,0,1}, {1,1,1}, {0,0,1}, {1,1,1}, {0,1,1} }, // 1: Vorne  (Z+)
                { {0,0,0}, {0,0,1}, {0,1,1}, {0,0,0}, {0,1,1}, {0,1,0} }, // 2: Links  (X-)
                { {1,0,1}, {1,0,0}, {1,1,0}, {1,0,1}, {1,1,0}, {1,1,1} }, // 3: Rechts (X+)
                { {0,1,0}, {0,1,1}, {1,1,1}, {0,1,0}, {1,1,1}, {1,1,0} }, // 4: Oben   (Y+)
                { {0,0,0}, {1,0,0}, {1,0,1}, {0,0,0}, {1,0,1}, {0,0,1} }  // 5: Unten  (Y-)
        };

        int[][] uvCoordsForFace = {
                {0,0}, {0,1}, {1,1}, {0,0}, {1,1}, {1,0}
        };

        int[][] neighborOffsets = {
                {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}
        };

        Vector3i globalVoxelPos = new Vector3i();
        Vector3i neighborChunkPos = new Vector3i();
        Vector3i localNeighborPos = new Vector3i();

        for (int x = 0; x < dims.x; x++) {
            for (int y = 0; y < dims.y; y++) {
                for (int z = 0; z < dims.z; z++) {

                    byte blockType = chunk.getVoxel(x, y, z);
                    if (blockType == 0) continue;

                    for (int face = 0; face < 6; face++) {
                        int nx = x + neighborOffsets[face][0];
                        int ny = y + neighborOffsets[face][1];
                        int nz = z + neighborOffsets[face][2];

                        byte neighborType = 0;

                        if (nx < 0 || nx >= dims.x || ny < 0 || ny >= dims.y || nz < 0 || nz >= dims.z) {
                            globalVoxelPos.set(chunkPos.x * dims.x + nx, chunkPos.y * dims.y + ny, chunkPos.z * dims.z + nz);
                            neighborChunkPos.set((int) Math.floor((double) globalVoxelPos.x / dims.x), (int) Math.floor((double) globalVoxelPos.y / dims.y), (int) Math.floor((double) globalVoxelPos.z / dims.z));

                            VoxelChunk neighborChunk = level.getActiveChunks().get(neighborChunkPos);
                            if (neighborChunk != null) {
                                localNeighborPos.set(globalVoxelPos.x - (neighborChunkPos.x * dims.x), globalVoxelPos.y - (neighborChunkPos.y * dims.y), globalVoxelPos.z - (neighborChunkPos.z * dims.z));
                                neighborType = neighborChunk.getVoxel(localNeighborPos.x, localNeighborPos.y, localNeighborPos.z);
                            }
                        } else {
                            neighborType = chunk.getVoxel(nx, ny, nz);
                        }

                        // --- KORREKTES CULLING NACH MATERIAL ---
                        boolean isVisible = false;
                        if (blockType == 4) { // Aktueller Block ist Wasser
                            // Wasser ist nur an Luft (0) sichtbar
                            isVisible = (neighborType == 0);
                        } else { // Aktueller Block ist Solide (Gras, Erde, Stein)
                            // Solide Blöcke zeigen ihre Wand an Luft (0) ODER an Wasser (4)
                            isVisible = (neighborType == 0 || neighborType == 4);
                        }

                        if (isVisible) {
                            for (int v = 0; v < 6; v++) {
                                int vx = x + faceVertices[face][v][0];
                                int vy = y + faceVertices[face][v][1];
                                int vz = z + faceVertices[face][v][2];

                                int u = uvCoordsForFace[v][0];
                                int vCoord = uvCoordsForFace[v][1];

                                int uvPackedIdx = u | (vCoord << 1);

                                int packedVertex = 0;
                                packedVertex |= (vx & 0x3F);
                                packedVertex |= (vy & 0x3F) << 6;
                                packedVertex |= (vz & 0x3F) << 12;
                                packedVertex |= (face & 0x07) << 18;
                                packedVertex |= (uvPackedIdx & 0x03) << 21;
                                packedVertex |= ((int) blockType & 0xFF) << 23;

                                // Sortierung in die richtige Liste basierend auf dem Blocktyp
                                if (blockType == 4) {
                                    waterVertices.add(packedVertex);
                                } else {
                                    solidVertices.add(packedVertex);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Erzeuge die beiden OpenGL Meshes über eine Hilfsmethode
        Mesh solidMesh = buildGLMesh(solidVertices);
        Mesh waterMesh = buildGLMesh(waterVertices);

        return new ChunkMeshResult(solidMesh, waterMesh);
    }

    // Hilfsmethode zum Bauen eines VBO/VAO aus einer Integer-Liste
    private static Mesh buildGLMesh(List<Integer> vertices) {
        if (vertices.isEmpty()) {
            return new Mesh(0, 0, 0, 0);
        }
        int[] finalVertices = vertices.stream().mapToInt(i -> i).toArray();
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, finalVertices, GL_STATIC_DRAW);
        glVertexAttribIPointer(0, 1, GL_INT, 0, 0);
        glEnableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        return new Mesh(vao, vbo, 0, finalVertices.length);
    }
}