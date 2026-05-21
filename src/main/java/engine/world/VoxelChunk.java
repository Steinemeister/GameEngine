package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.primitives.AABBf;

import java.util.BitSet;

public class VoxelChunk {
    private final Vector3i dimensions;
    private final Vector3i chunkPosition;
    private final BitSet voxels;
    private final AABBf bounds;
    private final Level level;
    private Mesh mesh;

    public VoxelChunk(Vector3i dimensions, Vector3i chunkPosition, Level level) {
        this.dimensions = new Vector3i(dimensions);
        this.chunkPosition = new Vector3i(chunkPosition);
        this.level = level;

        int totalVoxels = dimensions.x * dimensions.y * dimensions.z;
        this.voxels = new BitSet(totalVoxels);

        float minX = chunkPosition.x * dimensions.x;
        float minY = chunkPosition.y * dimensions.y;
        float minZ = chunkPosition.z * dimensions.z;
        this.bounds = new AABBf(
                minX, minY, minZ,
                minX + dimensions.x, minY + dimensions.y, minZ + dimensions.z
        );

        this.reGenMesh();
    }

    public void reGenMesh() {
        if (this.mesh != null) {
            this.mesh.cleanup();
        }
        this.mesh = ChunkMeshGenerator.generateMesh(this, level);
    }

    private int getIndex(int x, int y, int z) {
        if (!isLocalCoordinateInBounds(x, y, z)) {
            throw new IndexOutOfBoundsException("Local voxel coordinate out of bounds.");
        }
        return x + (y * dimensions.x) + (z * dimensions.x * dimensions.y);
    }

    // --- Core API ---
    public void setVoxel(int x, int y, int z, boolean active) {
        if (!getVoxel(x, y, z)) {
            voxels.set(getIndex(x, y, z), active);
            this.reGenMesh();
        }
    }

    public boolean getVoxel(int x, int y, int z) {
        return voxels.get(getIndex(x, y, z));
    }

    // --- Coordinate Transformation Helpers ---

    /**
     * Converts a global world position into a local voxel coordinate inside this chunk.
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

    /**
     * Converts a local voxel coordinate into a global world coordinate system.
     */
    public Vector3f localToWorldPosition(int x, int y, int z, Vector3f outWorldPos) {
        outWorldPos.x = (chunkPosition.x * dimensions.x) + x;
        outWorldPos.y = (chunkPosition.y * dimensions.y) + y;
        outWorldPos.z = (chunkPosition.z * dimensions.z) + z;
        return outWorldPos;
    }

    // --- Bounds and Neighbor Checking Helpers ---

    /**
     * Checks if a local voxel coordinate sits inside the limits of this specific chunk.
     */
    public boolean isLocalCoordinateInBounds(int x, int y, int z) {
        return x >= 0 && x < dimensions.x &&
                y >= 0 && y < dimensions.y &&
                z >= 0 && z < dimensions.z;
    }

    /**
     * Checks if a world position is inside the boundaries of this entire chunk.
     */
    public boolean containsWorldPosition(Vector3f worldPos) {
        return bounds.isValid() && bounds.containsPoint(worldPos);
    }

    /**
     * Finds the world grid position of a chunk given any absolute world position.
     */
    public static Vector3i getChunkPositionFromWorld(Vector3f worldPos, Vector3i chunkDimensions, Vector3i outChunkPos) {
        outChunkPos.x = (int) Math.floor(worldPos.x / chunkDimensions.x);
        outChunkPos.y = (int) Math.floor(worldPos.y / chunkDimensions.y);
        outChunkPos.z = (int) Math.floor(worldPos.z / chunkDimensions.z);
        return outChunkPos;
    }

    // --- Data Utilities ---

    public boolean isEmpty() {
        return voxels.isEmpty();
    }

    public void clear() {
        voxels.clear();
        this.reGenMesh();
    }

    public void fill() {
        int totalVoxels = dimensions.x * dimensions.y * dimensions.z;
        voxels.set(0, totalVoxels, true);
        this.reGenMesh();
    }

    // --- Getters ---
    public Vector3i getDimensions() {
        return dimensions;
    }
    public Vector3i getChunkPosition() {
        return chunkPosition;
    }
    public AABBf getBounds() {
        return bounds;
    }
    public Mesh getMesh() {
        return mesh;
    }

    public void setMesh(Mesh newMesh) {
        if (this.mesh != null) {
            this.mesh.cleanup(); // Wichtig gegen Speicherlecks!
        }
        this.mesh = newMesh;
    }

    /**
     * Gibt den Grafikspeicher frei, wenn der Chunk entladen oder das Spiel beendet wird.
     */
    public void cleanup() {
        if (this.mesh != null) {
            this.mesh.cleanup();
            this.mesh = null;
        }
    }

}
