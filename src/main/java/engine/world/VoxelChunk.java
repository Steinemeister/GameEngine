package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.GL_R8UI;
import static org.lwjgl.opengl.GL30.GL_RED_INTEGER;

public class VoxelChunk {
    private final Vector3i dimensions;
    private final Vector3i chunkPosition;
    private final byte[] voxels;

    private Mesh solidMesh;
    private Mesh waterMesh;

    private volatile boolean readyToUpload = false;
    private boolean isFullyOccluded = false;

    private final Map<Integer, byte[]> lodVoxelsCache = new HashMap<>();
    private final Map<Integer, Mesh> solidMeshCache = new HashMap<>();
    private final Map<Integer, Mesh> waterMeshCache = new HashMap<>();

    private int currentLod = 1;

    public VoxelChunk(Vector3i dimensions, Vector3i chunkPosition) {
        this.dimensions = new Vector3i(dimensions);
        this.chunkPosition = new Vector3i(chunkPosition);
        this.voxels = new byte[dimensions.x * dimensions.y * dimensions.z];
    }

    private int getIndex(int x, int y, int z) {
        return x + (y * dimensions.x) + (z * dimensions.x * dimensions.y);
    }

    private int getLodIndex(int x, int y, int z, Vector3i lodDim) {
        return x + (y * lodDim.x) + (z * lodDim.x * lodDim.y);
    }

    public void setVoxel(int x, int y, int z, byte blockId) {
        if (x >= 0 && x < dimensions.x && y >= 0 && y < dimensions.y && z >= 0 && z < dimensions.z) {
            voxels[getIndex(x, y, z)] = blockId;
            clearAllCaches();
        }
    }

    public byte getVoxel(int x, int y, int z) {
        if (x >= 0 && x < dimensions.x && y >= 0 && y < dimensions.y && z >= 0 && z < dimensions.z) {
            return voxels[getIndex(x, y, z)];
        }
        return 0;
    }

    public Vector3i getLodDimensions(Vector3i outDimensions) {
        outDimensions.x = Math.max(1, dimensions.x / currentLod);
        outDimensions.y = Math.max(1, dimensions.y / currentLod);
        outDimensions.z = Math.max(1, dimensions.z / currentLod);
        return outDimensions;
    }

    public byte[] getOrCreateLodArray(int lod) {
        if (lod <= 1) return voxels;
        if (lodVoxelsCache.containsKey(lod)) return lodVoxelsCache.get(lod);

        Vector3i lodDim = new Vector3i(
                Math.max(1, dimensions.x / lod),
                Math.max(1, dimensions.y / lod),
                Math.max(1, dimensions.z / lod)
        );

        byte[] lodData = new byte[lodDim.x * lodDim.y * lodDim.z];

        for (int z = 0; z < lodDim.z; z++) {
            for (int y = 0; y < lodDim.y; y++) {
                for (int x = 0; x < lodDim.x; x++) {
                    int realX = x * lod;
                    int realY = y * lod;
                    int realZ = z * lod;

                    byte blockId = 0;
                    if (realX < dimensions.x && realY < dimensions.y && realZ < dimensions.z) {
                        blockId = voxels[getIndex(realX, realY, realZ)];
                    }
                    lodData[x + (y * lodDim.x) + (z * lodDim.x * lodDim.y)] = blockId;
                }
            }
        }

        lodVoxelsCache.put(lod, lodData);
        return lodData;
    }

    public Mesh getAnyAvailableSolidMesh() {
        Mesh current = solidMeshCache.get(currentLod);
        if (current != null) return current;

        // Fallback: Nimm das erstbeste Mesh, das wir im Cache finden
        for (Mesh mesh : solidMeshCache.values()) {
            if (mesh != null) return mesh;
        }
        return null;
    }

    public Mesh getAnyAvailableWaterMesh() {
        Mesh current = waterMeshCache.get(currentLod);
        if (current != null) return current;

        for (Mesh mesh : waterMeshCache.values()) {
            if (mesh != null) return mesh;
        }
        return null;
    }

    public void clearAllCaches() {
        lodVoxelsCache.clear();

        // Löscht die Meshes auch korrekt aus dem Grafikspeicher (VRAM)
        solidMeshCache.values().forEach(Mesh::cleanup);
        waterMeshCache.values().forEach(Mesh::cleanup);

        solidMeshCache.clear();
        waterMeshCache.clear();
        this.readyToUpload = false;
    }

    public void fill(byte blockId) {
        java.util.Arrays.fill(voxels, blockId);
    }


    public boolean hasMeshesForCurrentLod() {
        return solidMeshCache.containsKey(currentLod) || waterMeshCache.containsKey(currentLod);
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

    public Vector3i getDimensions() {
        return dimensions;
    }
    public Vector3i getChunkPosition() {
        return chunkPosition;
    }

    public void cleanup() {

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

    public int getLod() {
        return currentLod;
    }

    public void setLod(int lod) {
        if (lod < 1) lod = 1; // Sicherung gegen ungültige Werte

        if (this.currentLod != lod) {
            this.currentLod = lod;

            // Stellt sicher, dass das reduzierte Voxel-Array für dieses LOD im RAM existiert
            getOrCreateLodArray(lod);

            // Wenn für das neue LOD noch kein Mesh im Cache liegt,
            // markieren wir den Chunk als "bereit für den Hintergrund-Thread"
            this.readyToUpload = !hasMeshesForCurrentLod();
        }
    }
}