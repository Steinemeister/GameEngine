package engine.world;

import engine.object.Mesh;
import engine.worldGen.WorldGen;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Level {
    private final Vector3i chunkDimensions;

    // Thread-sichere Map für Chunks, da der Hintergrund-Thread darauf zugreift
    private final Map<Vector3i, VoxelChunk> activeChunks;

    // Thread-Pool für die Hintergrund-Berechnung (Nutzt so viele Threads wie CPU-Kerne vorhanden sind)
    private final ExecutorService threadPool;

    private final Set<Vector3i> chunksInGeneration = ConcurrentHashMap.newKeySet();

    private final WorldGen worldGen;

    public Level(Vector3i chunkDimensions) {
        this.chunkDimensions = new Vector3i(chunkDimensions);
        this.activeChunks = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);

        this.worldGen = new WorldGen(1337L);
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
        org.joml.Vector3i centerChunk = new org.joml.Vector3i();
        VoxelChunk.getChunkPositionFromWorld(centerWorldPos, chunkDimensions, centerChunk);

        java.util.Map<org.joml.Vector3i, VoxelChunk> newActiveChunks = new java.util.HashMap<>();

        // WICHTIG: Der Lade-Radius im Hintergrund ist um 1 größer als der Sicht-Radius!
        int hGenerationRadius = viewRadius + 1;
        int vGenerationRadius = viewRadius - 15;

        for (int x = -hGenerationRadius; x <= hGenerationRadius; x++) {
            for (int z = -hGenerationRadius; z <= hGenerationRadius; z++) {
                for (int y = -vGenerationRadius; y <= vGenerationRadius; y++) {
                    int cx = centerChunk.x + x;
                    int cy = centerChunk.y + y;
                    int cz = centerChunk.z + z;

                    org.joml.Vector3i chunkKey = new org.joml.Vector3i(cx, cy, cz);
                    VoxelChunk chunk = activeChunks.get(chunkKey);

                    if (chunk == null) {
                        // TRICK: .add() gibt true zurück, wenn die Position NEU ist.
                        // Wenn sie schon drin steht, wird der gesamte Block komplett übersprungen!
                        if (chunksInGeneration.add(chunkKey)) {
                            threadPool.submit(() -> {
                                try {
                                    VoxelChunk generatedChunk = worldGen.genChunkAt(chunkKey);
                                    if (generatedChunk != null) {
                                        generatedChunk.setReadyToUpload(true);
                                        activeChunks.put(chunkKey, generatedChunk);
                                    }
                                } finally {
                                    // Wenn der Thread fertig (oder abgestürzt) ist,
                                    // geben wir die Position für die Zukunft wieder frei
                                    chunksInGeneration.remove(chunkKey);
                                }
                            });
                        }
                    }

                    if (chunk != null &&
                            Math.abs(x) <= viewRadius &&
                            Math.abs(y) <= viewRadius &&
                            Math.abs(z) <= viewRadius) {
                        newActiveChunks.put(chunkKey, chunk);
                    }
                }

            }
        }

        // Chunks, die komplett aus dem erweiterten Generierungs-Radius fliegen, löschen
        for (Map.Entry<org.joml.Vector3i, VoxelChunk> entry : activeChunks.entrySet()) {
            org.joml.Vector3i k = entry.getKey();
            int dx = Math.abs(k.x - centerChunk.x);
            int dy = Math.abs(k.y - centerChunk.y);
            int dz = Math.abs(k.z - centerChunk.z);

            if (dx > hGenerationRadius || dy > hGenerationRadius || dz > hGenerationRadius) {
                VoxelChunk removed = activeChunks.remove(k);
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
        int maxUploadsPerFrame = 8;

        java.util.List<VoxelChunk> pendingChunks = new java.util.ArrayList<>();
        for (VoxelChunk chunk : activeChunks.values()) {
            if (chunk.isReadyToUpload() && chunk.getMesh() == null && !chunk.isFullyOccluded()) {
                pendingChunks.add(chunk);
            }
        }

        if (pendingChunks.isEmpty()) return;

        // Nach Nähe zum Spieler sortieren
        org.joml.Vector3i dims = getChunkDimensions();
        pendingChunks.sort((c1, c2) -> {
            float c1X = (c1.getChunkPosition().x * dims.x) + (dims.x * 0.5f);
            float c1Y = (c1.getChunkPosition().y * dims.y) + (dims.y * 0.5f);
            float c1Z = (c1.getChunkPosition().z * dims.z) + (dims.z * 0.5f);
            float distSq1 = playerWorldPos.distanceSquared(c1X, c1Y, c1Z);
            float c2X = (c2.getChunkPosition().x * dims.x) + (dims.x * 0.5f);
            float c2Y = (c2.getChunkPosition().y * dims.y) + (dims.y * 0.5f);
            float c2Z = (c2.getChunkPosition().z * dims.z) + (dims.z * 0.5f);
            float distSq2 = playerWorldPos.distanceSquared(c2X, c2Y, c2Z);
            return Float.compare(distSq1, distSq2);
        });

        int[][] directions = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
        org.joml.Vector3i neighborKey = new org.joml.Vector3i();

        for (VoxelChunk chunk : pendingChunks) {
            Mesh dummyMesh = ChunkMeshGenerator.generateMesh(chunk, this);
            chunk.setMesh(dummyMesh);

            // 1. Eigene Textur hochladen
            chunk.update3DTexture(this);
            chunk.setReadyToUpload(false);

            // 2. --- WICHTIG: DIE NACHBARN TRIGGERN ---
            org.joml.Vector3i cPos = chunk.getChunkPosition();
            for (int[] d : directions) {
                neighborKey.set(cPos.x + d[0], cPos.y + d[1], cPos.z + d[2]);
                VoxelChunk neighbor = activeChunks.get(neighborKey);

                // Wenn der Nachbar bereits auf der GPU gerendert wird, muss er seine
                // 3D-Textur neu beladen, um die Kanten-Werte von uns einzulesen!
                if (neighbor != null && neighbor.getMesh() != null) {
                    neighbor.update3DTexture(this);
                }
            }

            uploadsThisFrame++;
            if (uploadsThisFrame >= maxUploadsPerFrame) {
                break;
            }
        }
    }

    public VoxelChunk generateTerrainForChunk(Vector3i chunkPos) {
        return worldGen.genChunkAt(chunkPos);
    }

    public void generateAllMeshes() { /* Unused, da asynchron geladen wird */ }

    public void cleanup() {
        threadPool.shutdownNow(); // Threads beenden
        for (VoxelChunk chunk : activeChunks.values()) {
            chunk.cleanup();
        }
        activeChunks.clear();
    }

    public byte getVoxelAtWorld(org.joml.Vector3f worldPos) {
        org.joml.Vector3i chunkKey = new org.joml.Vector3i();
        VoxelChunk.getChunkPositionFromWorld(worldPos, chunkDimensions, chunkKey);

        VoxelChunk chunk = activeChunks.get(chunkKey);

        if (chunk != null) {
            // synchronized zwingt die CPU, den Cache aller Kerne sofort abzugleichen!
            // Das verhindert, dass unfertige oder veraltete Block-Daten gelesen werden.
            synchronized (chunk) {
                if (chunk.isReadyToUpload() || chunk.getMesh() != null) {
                    org.joml.Vector3i localPos = new org.joml.Vector3i();
                    chunk.worldToLocalCoordinate(worldPos, localPos);
                    return chunk.getVoxel(localPos.x, localPos.y, localPos.z);
                }
            }
        }

        return 0;
    }

    public boolean checkCollision(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Ermittle den Blockbereich, den die Box aktuell schneidet
        int startX = (int) Math.floor(minX);
        int endX   = (int) Math.floor(maxX);
        int startY = (int) Math.floor(minY);
        int endY   = (int) Math.floor(maxY);
        int startZ = (int) Math.floor(minZ);
        int endZ   = (int) Math.floor(maxZ);

        org.joml.Vector3f testPos = new org.joml.Vector3f();

        // Iteriere über alle Blöcke im betroffenen Bereich
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    testPos.set(x, y, z);

                    // Wenn an dieser Position ein solider Block (ID > 0) existiert -> Kollision!
                    if (getVoxelAtWorld(testPos) > 0) {
                        return true;
                    }
                }
            }
        }
        return false; // Keine Kollision gefunden
    }

    public Map<Vector3i, VoxelChunk> getActiveChunks() { return activeChunks; }
    public Vector3i getChunkDimensions() { return chunkDimensions; }
}
