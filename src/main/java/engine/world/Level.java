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
    public void uploadPendingTextures(Vector3f playerWorldPos) {
        int uploadsThisFrame = 0;
        int maxUploadsPerFrame = 2; // Stoßgrenze pro Frame

        // 1. Alle Chunks sammeln, die aktuell auf einen Upload warten und sichtbar sein können
        java.util.List<VoxelChunk> pendingChunks = new java.util.ArrayList<>();

        for (VoxelChunk chunk : activeChunks.values()) {
            if (chunk.isReadyToUpload() && chunk.getMesh() == null && !chunk.isFullyOccluded()) {
                pendingChunks.add(chunk);
            }
        }

        // Wenn nichts hochzuladen ist, direkt abbrechen
        if (pendingChunks.isEmpty()) {
            return;
        }

        // 2. Die Liste nach der Nähe zum Spieler sortieren (Niedrige Distanz zuerst)
        org.joml.Vector3i dims = getChunkDimensions();
        pendingChunks.sort((c1, c2) -> {
            // Mittelpunkt von Chunk 1 in Weltkoordinaten bestimmen
            float c1X = (c1.getChunkPosition().x * dims.x) + (dims.x * 0.5f);
            float c1Y = (c1.getChunkPosition().y * dims.y) + (dims.y * 0.5f);
            float c1Z = (c1.getChunkPosition().z * dims.z) + (dims.z * 0.5f);
            float distSq1 = playerWorldPos.distanceSquared(c1X, c1Y, c1Z);

            // Mittelpunkt von Chunk 2 in Weltkoordinaten bestimmen
            float c2X = (c2.getChunkPosition().x * dims.x) + (dims.x * 0.5f);
            float c2Y = (c2.getChunkPosition().y * dims.y) + (dims.y * 0.5f);
            float c2Z = (c2.getChunkPosition().z * dims.z) + (dims.z * 0.5f);
            float distSq2 = playerWorldPos.distanceSquared(c2X, c2Y, c2Z);

            // Sortierung aufsteigend (kleinste Distanz zuerst)
            return Float.compare(distSq1, distSq2);
        });

        // 3. Nur die am nächsten liegenden Chunks in diesem Frame verarbeiten
        for (VoxelChunk chunk : pendingChunks) {
            Mesh dummyMesh = ChunkMeshGenerator.generateMesh(chunk, this);
            chunk.setMesh(dummyMesh);
            chunk.update3DTexture();

            chunk.setReadyToUpload(false); // Aus der Warteschlange entfernen
            uploadsThisFrame++;

            if (uploadsThisFrame >= maxUploadsPerFrame) {
                break;
            }
        }
    }

    public void generateTerrainForChunk(VoxelChunk chunk) {
        org.joml.Vector3i chunkPos = chunk.getChunkPosition();
        org.joml.Vector3i dims = chunk.getDimensions();

        int worldXOffset = chunkPos.x * dims.x;
        int worldZOffset = chunkPos.z * dims.z;
        int worldYOffset = chunkPos.y * dims.y;

        float frequency = 0.015f;
        float maxMountainHeight = 40.0f;
        float baseWaterLevel = -10.0f;

        int absoluteMinTerrainHeight = (int) baseWaterLevel;
        int absoluteMaxTerrainHeight = (int) (baseWaterLevel + maxMountainHeight);

        if (worldYOffset > absoluteMaxTerrainHeight) {
            chunk.fill((byte) 0);
            chunk.setFullyOccluded(true);
            return;
        }

        // Achtung: Wenn wir Höhlen im Untergrund haben, dürfen wir Chunks im tiefen Stein
        // NICHT mehr blind mit fill(3) füllen, da sie sonst keine Höhlenlöcher enthalten!

        boolean hasVoxels = false;

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

                        // --- 3D HÖHLEN-RAUSCHEN ---
                        // Höhlen-Frequenz (höher = kleinere, komplexere Gänge)
                        float caveFreq = 0.06f;
                        float caveNoise = PerlinNoiseGenerator.noise3D(globalX * caveFreq, globalY * caveFreq, globalZ * caveFreq);

                        // Schwellenwert: Wenn caveNoise > 0.45, höhlen wir den Block aus (wird zu Luft)
                        // Der "globalY < targetHeight - 4" Check sorgt dafür, dass keine Löcher in der Wiese entstehen
                        if (caveNoise > 0.45f && globalY < targetHeight - 4) {
                            blockId = 0;
                        }
                    }

                    if (blockId > 0) hasVoxels = true;
                    chunk.setVoxel(x, y, z, blockId);
                }
            }
        }

        // Wenn der gesamte Chunk nach dem Aushöhlen nur aus Luft besteht, Rendern komplett unterbinden
        if (!hasVoxels) {
            chunk.setFullyOccluded(true);
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
