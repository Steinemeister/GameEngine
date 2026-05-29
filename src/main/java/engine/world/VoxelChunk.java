package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.GL_R8UI;
import static org.lwjgl.opengl.GL30.GL_RED_INTEGER;

public class VoxelChunk {
    private final Vector3i dimensions;
    private final Vector3i chunkPosition;
    private final byte[] voxels; // Speichert Block-IDs (0 = Luft, 1 = Stein, 2 = Gras, etc.)

    private int texture3DId = 0; // Die ID der 3D-Textur auf der GPU
    private Mesh solidMesh;
    private Mesh waterMesh;

    private volatile boolean readyToUpload = false;
    private boolean isFullyOccluded = false;

    public VoxelChunk(Vector3i dimensions, Vector3i chunkPosition) {
        this.dimensions = new Vector3i(dimensions);
        this.chunkPosition = new Vector3i(chunkPosition);
        this.voxels = new byte[dimensions.x * dimensions.y * dimensions.z];
    }

    private int getIndex(int x, int y, int z) {
        return x + (y * dimensions.x) + (z * dimensions.x * dimensions.y);
    }

    public void setVoxel(int x, int y, int z, byte blockId) {
        if (x >= 0 && x < dimensions.x && y >= 0 && y < dimensions.y && z >= 0 && z < dimensions.z) {
            voxels[getIndex(x, y, z)] = blockId;
        }
    }

    public byte getVoxel(int x, int y, int z) {
        if (x >= 0 && x < dimensions.x && y >= 0 && y < dimensions.y && z >= 0 && z < dimensions.z) {
            return voxels[getIndex(x, y, z)];
        }
        return 0; // Luft außerhalb der Grenzen
    }

    public void fill(byte blockId) {
        java.util.Arrays.fill(voxels, blockId);
    }

    /**
     * Lädt die Voxel-Daten als 3D-Textur direkt auf die GPU hoch.
     */
    public void update3DTexture(Level level) {
        int texW = dimensions.x + 2;
        int texH = dimensions.y + 2;
        int texD = dimensions.z + 2;

        // 1. Daten im Buffer sammeln
        java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(texW * texH * texD);
        org.joml.Vector3f tempWorldPos = new org.joml.Vector3f();

        float worldXOffset = (float) (chunkPosition.x * dimensions.x);
        float worldYOffset = (float) (chunkPosition.y * dimensions.y);
        float worldZOffset = (float) (chunkPosition.z * dimensions.z);

        for (int z = -1; z <= dimensions.z; z++) {
            for (int y = -1; y <= dimensions.y; y++) {
                for (int x = -1; x <= dimensions.x; x++) {
                    byte blockId;
                    if (x >= 0 && x < dimensions.x && y >= 0 && y < dimensions.y && z >= 0 && z < dimensions.z) {
                        blockId = getVoxel(x, y, z);
                    } else {
                        tempWorldPos.set(worldXOffset + x, worldYOffset + y, worldZOffset + z);
                        blockId = level.getVoxelAtWorld(tempWorldPos);
                    }
                    buffer.put(blockId);
                }
            }
        }
        buffer.flip();

        // 2. ABSOLUT CONCURRENT-SICHERER UPLOAD AN OPENGL
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (texture3DId == 0) {
            // Textur komplett neu anlegen (beim ersten Mal)
            texture3DId = glGenTextures();
            glBindTexture(GL_TEXTURE_3D, texture3DId);

            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

            glTexImage3D(GL_TEXTURE_3D, 0, GL_R8UI, texW, texH, texD,
                    0, GL_RED_INTEGER, GL_UNSIGNED_BYTE, buffer);
        } else {
            // Die Textur existiert bereits -> Nur die Pixel im Speicher austauschen!
            // Das verhindert, dass der NVIDIA-Treiber den Chunk unsichtbar macht!
            glBindTexture(GL_TEXTURE_3D, texture3DId);
            glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, texW, texH, texD,
                    GL_RED_INTEGER, GL_UNSIGNED_BYTE, buffer);
        }

        glBindTexture(GL_TEXTURE_3D, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    }

    public static Vector3i getChunkPositionFromWorld(Vector3f worldPos, Vector3i chunkDimensions, Vector3i outChunkPos) {
        outChunkPos.x = (int) Math.floor(worldPos.x / chunkDimensions.x);
        outChunkPos.y = (int) Math.floor(worldPos.y / chunkDimensions.y);
        outChunkPos.z = (int) Math.floor(worldPos.z / chunkDimensions.z);
        return outChunkPos;
    }

    /**
     * Wandelt eine globale Weltposition in eine lokale Voxel-Koordinate (0 bis Dimension-1)
     * innerhalb dieses spezifischen Chunks um.
     */
    public Vector3i worldToLocalCoordinate(Vector3f worldPos, Vector3i outLocalPos) {
        int worldX = (int) Math.floor(worldPos.x);
        int worldY = (int) Math.floor(worldPos.y);
        int worldZ = (int) Math.floor(worldPos.z);

        outLocalPos.x = worldX - (chunkPosition.x * dimensions.x);
        outLocalPos.y = worldY - (chunkPosition.y * dimensions.y);
        outLocalPos.z = worldZ - (chunkPosition.z * dimensions.z);
        return outLocalPos;
    }

    public int getTexture3DId() {
        return texture3DId;
    }
    public Vector3i getDimensions() {
        return dimensions;
    }
    public Vector3i getChunkPosition() {
        return chunkPosition;
    }

    public void cleanup() {
        if (texture3DId != 0) {
            glDeleteTextures(texture3DId);
            texture3DId = 0;
        }
    }

    public Mesh getSolidMesh() {
        return solidMesh;
    }

    public void setSolidMesh(Mesh mesh) {
        this.solidMesh = mesh;
    }

    public Mesh getWaterMesh() {
        return waterMesh;
    }

    public void setWaterMesh(Mesh mesh) {
        this.waterMesh = mesh;
    }

    public boolean isReadyToUpload() {
        return readyToUpload;
    }
    public void setReadyToUpload(boolean ready) {
        this.readyToUpload = ready;
    }

    public boolean isFullyOccluded() {
        return isFullyOccluded;
    }
    public void setFullyOccluded(boolean occluded) {
        this.isFullyOccluded = occluded;
    }
}
