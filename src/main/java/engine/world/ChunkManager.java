package engine.world;

import java.util.HashMap;
import java.util.Map;

public class ChunkManager {
    private final Map<Long, Chunk> loadedChunks = new HashMap<>();

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public void addChunk(Chunk chunk) {
        long key = getChunkKey(chunk.getChunkX(), chunk.getChunkZ());
        loadedChunks.put(key, chunk);
    }

    public Chunk getChunk(int x, int z) {
        long key = getChunkKey(x, z);
        return loadedChunks.get(key);
    }

    public boolean isChunkLoaded(int x, int z) {
        long key = getChunkKey(x, z);
        return loadedChunks.containsKey(key);
    }

    public void unloadChunk(int x, int z) {
        long key = getChunkKey(x, z);
        Chunk chunk = loadedChunks.remove(key);
        if (chunk != null && chunk.getMesh() != null) {
            // WICHTIG: Mesh-Cleanup-Task muss an den Render-Thread geschickt werden,
            // da der Main-Thread keine OpenGL-Buffer löschen darf!
        }
    }

    public Map<Long, Chunk> getLoadedChunks() {
        return loadedChunks;
    }
}
