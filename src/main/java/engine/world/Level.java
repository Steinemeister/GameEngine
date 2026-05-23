package engine.world;

import engine.object.Mesh;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Level {
    private final Vector3i chunkDimensions;

    // Thread-sichere Map für Chunks, da der Hintergrund-Thread darauf zugreift
    private final Map<Vector3i, VoxelChunk> activeChunks;

    // Thread-Pool für die Hintergrund-Berechnung (Nutzt so viele Threads wie CPU-Kerne vorhanden sind)
    private final ExecutorService threadPool;

    public Level(Vector3i chunkDimensions) {
        this.chunkDimensions = new Vector3i(chunkDimensions);
        this.activeChunks = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public VoxelChunk loadChunk(int cx, int cy, int cz) {
        Vector3i pos = new Vector3i(cx, cy, cz);
        if (activeChunks.containsKey(pos)) {
            return activeChunks.get(pos);
        }
        VoxelChunk chunk = new VoxelChunk(chunkDimensions, pos);
        activeChunks.put(pos, chunk);
        return chunk;
    }

    /**
     * Scannt die Welt im 3D-Radius ab und lagert die rechenintensive Generierung
     * auf Hintergrund-Threads aus.
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
                        // Chunk leer erzeugen und sofort in die Map eintragen
                        VoxelChunk newChunk = new VoxelChunk(chunkDimensions, chunkKey);
                        activeChunks.put(chunkKey, newChunk);
                        chunk = newChunk;

                        // Die schwere Perlin-Noise Arbeit in den Hintergrund-Thread auslagern!
                        threadPool.submit(() -> {
                            generateTerrainForChunk(newChunk);
                            // Flag setzen, dass der Chunk bereit für OpenGL ist
                            newChunk.setReadyToUpload(true);
                        });
                    }

                    newActiveChunks.put(chunkKey, chunk);
                }
            }
        }

        // Ausgelaufene Chunks löschen
        for (Map.Entry<Vector3i, VoxelChunk> entry : activeChunks.entrySet()) {
            if (!newActiveChunks.containsKey(entry.getKey())) {
                VoxelChunk removed = activeChunks.remove(entry.getKey());
                if (removed != null) removed.cleanup();
            }
        }
    }

    /**
     * Diese Methode läuft auf dem Haupt-Thread im Render-Loop. Sie prüft, welche
     * Hintergrund-Chunks fertig sind und lädt deren Texturen/VAOs stoßfrei auf die GPU.
     */
    public void uploadPendingTextures() {
        for (VoxelChunk chunk : activeChunks.values()) {
            // Wenn der Noise fertig ist, aber das Mesh/die Textur noch fehlt
            if (chunk.isReadyToUpload() && chunk.getMesh() == null) {
                Mesh dummyMesh = ChunkMeshGenerator.generateMesh(chunk, this);
                chunk.setMesh(dummyMesh);
                chunk.update3DTexture();
                chunk.setReadyToUpload(false); // Fertig hochgeladen
            }
        }
    }

    public void generateTerrainForChunk(VoxelChunk chunk) {
        Vector3i chunkPos = chunk.getChunkPosition();
        Vector3i dims = chunk.getDimensions();

        int worldXOffset = chunkPos.x * dims.x;
        int worldZOffset = chunkPos.z * dims.z;
        int worldYOffset = chunkPos.y * dims.y;

        float frequency = 0.015f; // Frequenz halbiert für majestätischere, größere Berge bei hoher Sichtweite
        float maxMountainHeight = 40.0f; // Höhere Berge passend zur Render Distance
        float baseWaterLevel = -10.0f;

        for (int x = 0; x < dims.x; x++) {
            for (int z = 0; z < dims.z; z++) {
                float globalX = worldXOffset + x;
                float globalZ = worldZOffset + z;

                float noiseVal = PerlinNoiseGenerator.noise2D(globalX * frequency, globalZ * frequency);
                int targetHeight = (int) (baseWaterLevel + ((noiseVal + 1.0f) * 0.5f) * maxMountainHeight);

                for (int y = 0; y < dims.y; y++) {
                    int globalY = worldYOffset + y;
                    byte blockId = 0;

                    if (globalY <= targetHeight) {
                        if (globalY == targetHeight) {
                            blockId = 1; // Gras
                        } else if (globalY > targetHeight - 4) {
                            blockId = 2; // Erde
                        } else {
                            blockId = 3; // Stein
                        }
                    }
                    chunk.setVoxel(x, y, z, blockId);
                }
            }
        }
    }

    public void generateAllMeshes() { /* Unused, da asynchron geladen wird */ }

    public void cleanup() {
        threadPool.shutdownNow(); // Threads beenden
        for (VoxelChunk chunk : activeChunks.values()) {
            chunk.cleanup();
        }
        activeChunks.clear();
    }

    public Map<Vector3i, VoxelChunk> getActiveChunks() { return activeChunks; }
    public Vector3i getChunkDimensions() { return chunkDimensions; }
}
