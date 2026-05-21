package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.Map;

public class Level {
    private final Vector3i chunkDimensions;
    private final Map<Vector3i, VoxelChunk> activeChunks;

    public Level(Vector3i chunkDimensions) {
        this.chunkDimensions = new Vector3i(chunkDimensions);
        this.activeChunks = new HashMap<>();
    }

    /**
     * Creates and loads a chunk at the specified chunk grid position if it doesn't exist.
     */
    public VoxelChunk loadChunk(int cx, int cy, int cz) {
        Vector3i pos = new Vector3i(cx, cy, cz);
        if (activeChunks.containsKey(pos)) {
            return activeChunks.get(pos);
        }
        VoxelChunk chunk = new VoxelChunk(chunkDimensions, pos, this);
        activeChunks.put(pos, chunk);
        return chunk;
    }

    /**
     * Unloads a chunk from memory.
     */
    public void unloadChunk(int cx, int cy, int cz) {
        activeChunks.remove(new Vector3i(cx, cy, cz));
    }

    /**
     * Sets a voxel state using global world coordinates, automatically routing
     * the request to the correct chunk.
     */
    public void setVoxelAtWorld(Vector3f worldPos, boolean active) {
        Vector3i chunkKey = new Vector3i();
        VoxelChunk.getChunkPositionFromWorld(worldPos, chunkDimensions, chunkKey);
        VoxelChunk chunk = activeChunks.get(chunkKey);

        if (chunk != null) {
            Vector3i localPos = new Vector3i();
            chunk.worldToLocalCoordinate(worldPos, localPos);
            chunk.setVoxel(localPos.x, localPos.y, localPos.z, active);
        }
    }

    /**
     * Gets a voxel state using global world coordinates. Returns false if the
     * targeted chunk is not loaded.
     */
    public boolean getVoxelAtWorld(Vector3f worldPos) {
        Vector3i chunkKey = new Vector3i();
        VoxelChunk.getChunkPositionFromWorld(worldPos, chunkDimensions, chunkKey);
        VoxelChunk chunk = activeChunks.get(chunkKey);

        if (chunk != null) {
            Vector3i localPos = new Vector3i();
            chunk.worldToLocalCoordinate(worldPos, localPos);
            return chunk.getVoxel(localPos.x, localPos.y, localPos.z);
        }
        return false;
    }

    /**
     * Retrieves a chunk at the specified grid position.
     */
    public VoxelChunk getChunk(int cx, int cy, int cz) {
        return activeChunks.get(new Vector3i(cx, cy, cz));
    }

    /**
     * Automatically loads chunks within a specific radius around a world position (e.g., a player)
     * and unloads chunks that fall outside that radius.
     */
    public void updateVisibleChunks(Vector3f centerWorldPos, int viewRadius) {
        Vector3i centerChunk = new Vector3i();
        VoxelChunk.getChunkPositionFromWorld(centerWorldPos, chunkDimensions, centerChunk);

        Map<Vector3i, VoxelChunk> newActiveChunks = new HashMap<>();

        for (int x = -viewRadius; x <= viewRadius; x++) {
            for (int y = -viewRadius; y <= viewRadius; y++) {
                for (int z = -viewRadius; z <= viewRadius; z++) {
                    int cx = centerChunk.x + x;
                    int cy = centerChunk.y + y;
                    int cz = centerChunk.z + z;

                    Vector3i chunkKey = new Vector3i(cx, cy, cz);

                    VoxelChunk chunk = activeChunks.get(chunkKey);
                    if (chunk == null) {
                        chunk = new VoxelChunk(chunkDimensions, chunkKey, this);
                    }
                    newActiveChunks.put(chunkKey, chunk);
                }
            }
        }

        activeChunks.clear();
        activeChunks.putAll(newActiveChunks);
    }

    public void cleanup() {
        // Iteriere über alle geladenen Chunks und rufe deren Cleanup auf
        for (VoxelChunk chunk : activeChunks.values()) {
            chunk.cleanup();
        }
        // Leere die Map, da die Chunks nun ungültige Grafik-Ressourcen enthalten
        activeChunks.clear();
    }

    // --- Getters ---
    public Map<Vector3i, VoxelChunk> getActiveChunks() {
        return activeChunks;
    }

    public Vector3i getChunkDimensions() {
        return chunkDimensions;
    }
}
