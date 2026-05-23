package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL30.GL_R8UI;
import static org.lwjgl.opengl.GL30.GL_RED_INTEGER;

public class VoxelChunk {
    private final Vector3i dimensions;
    private final Vector3i chunkPosition;
    private final byte[] voxels; // Speichert Block-IDs (0 = Luft, 1 = Stein, 2 = Gras, etc.)

    private int texture3DId = 0; // Die ID der 3D-Textur auf der GPU
    private Mesh chunkMesh;      // Unser "leeres" oder instanziertes Mesh

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
    public void update3DTexture() {
        if (texture3DId == 0) {
            texture3DId = glGenTextures();
        }

        glBindTexture(GL_TEXTURE_3D, texture3DId);

        // Parameter für exakte Block-Kanten (keine Filterung/Glättung zwischen Blöcken)
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        // Daten in einen Byte-Buffer kopieren
        ByteBuffer buffer = BufferUtils.createByteBuffer(voxels.length);
        buffer.put(voxels);
        buffer.flip();

        // Als R8UI (8-Bit Unsigned Integer, Single Channel) spezifizieren
        glTexImage3D(
                GL_TEXTURE_3D,
                0,
                GL_R8UI,            // 1. Internes GPU-Format: Unsigned Integer
                dimensions.x,
                dimensions.y,
                dimensions.z,
                0,
                GL_RED_INTEGER,     // 2. Format der Java-Daten: Wichtig ist das _INTEGER!
                GL_UNSIGNED_BYTE,   // 3. Datentyp: byte
                buffer
        );

        glBindTexture(GL_TEXTURE_3D, 0);
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

    public Mesh getMesh() {
        return chunkMesh;
    }

    public void setMesh(Mesh mesh) {
        this.chunkMesh = mesh;
    }
}
