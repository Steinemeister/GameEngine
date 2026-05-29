package engine.world;

import engine.object.Mesh;
import engine.util.RaycastResult;
import engine.worldGen.WorldGen;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Level {
    private final Vector3i chunkDimensions;

    private final Map<Vector3i, VoxelChunk> activeChunks;

    private final ExecutorService threadPool;

    private final Set<String> chunksInGeneration = ConcurrentHashMap.newKeySet();

    private final WorldGen worldGen;

    public Level(Vector3i chunkDimensions) {
        this.chunkDimensions = new Vector3i(chunkDimensions);
        this.activeChunks = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);

        this.worldGen = new WorldGen(new Random().nextLong());
    }

    private String makeGenKey(Vector3i pos, int lod) {
        return pos.x + "," + pos.y + "," + pos.z + "_" + lod;
    }

    private int calculateTargetLod(int x, int y, int z, int viewRadius) {
        int maxDist = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (maxDist <= viewRadius / 3) {
            return 1;  // Volle Details nah am Spieler
        } else if (maxDist <= (viewRadius * 2) / 3) {
            return 2;  // Mittlere Details
        } else {
            return 4;  // Geringe Details auf Distanz
        }
    }



    public VoxelChunk loadChunk(int cx, int cy, int cz) {
        Vector3i pos = new Vector3i(cx, cy, cz);
        return activeChunks.computeIfAbsent(pos, p -> new VoxelChunk(chunkDimensions, p));
    }

    public void updateVisibleChunks(Vector3f centerWorldPos, int viewRadius) {
        Vector3i centerChunk = new Vector3i();
        VoxelChunk.getChunkPositionFromWorld(centerWorldPos, chunkDimensions, centerChunk);

        int hGenerationRadius = viewRadius + 1;
        int vGenerationRadius = viewRadius - 15;

        for (int x = -hGenerationRadius; x <= hGenerationRadius; x++) {
            for (int z = -hGenerationRadius; z <= hGenerationRadius; z++) {
                for (int y = -vGenerationRadius; y <= vGenerationRadius; y++) {
                    int cx = centerChunk.x + x;
                    int cy = centerChunk.y + y;
                    int cz = centerChunk.z + z;

                    Vector3i chunkKey = new Vector3i(cx, cy, cz);
                    int targetLod = calculateTargetLod(x, y, z, viewRadius);
                    VoxelChunk chunk = activeChunks.get(chunkKey);

                    // Fall 1: Der Chunk existiert noch gar nicht
                    if (chunk == null) {
                        String genKey = makeGenKey(chunkKey, targetLod);
                        if (chunksInGeneration.add(genKey)) {
                            threadPool.submit(() -> {
                                try {
                                    VoxelChunk generatedChunk = worldGen.genChunkAt(chunkKey);
                                    if (generatedChunk != null) {
                                        generatedChunk.setLod(targetLod);
                                        // ASYNCHRONES MESHING: Direkt im Thread berechnen
                                        ChunkMeshGenerator.ChunkMeshResult result = ChunkMeshGenerator.generateMeshes(generatedChunk, this);
                                        generatedChunk.setSolidMesh(result.solidMesh());
                                        generatedChunk.setWaterMesh(result.waterMesh());

                                        generatedChunk.setReadyToUpload(true);
                                        activeChunks.put(chunkKey, generatedChunk);
                                    }
                                } finally {
                                    chunksInGeneration.remove(genKey);
                                }
                            });
                        }
                    }
                    // Fall 2: Chunk existiert, benötigt aber ein anderes LOD (z.B. Spieler nähert sich)
                    else {
                        int oldLod = chunk.getLod();
                        chunk.setLod(targetLod);

                        // Wenn für das neue Ziel-LOD noch keine Meshes existieren, im Hintergrund anfordern
                        if (!chunk.hasMeshesForCurrentLod()) {
                            String genKey = makeGenKey(chunkKey, targetLod);
                            if (chunksInGeneration.add(genKey)) {
                                final VoxelChunk finalChunk = chunk;
                                threadPool.submit(() -> {
                                    try {
                                        // Erzwinge die Erstellung des kleineren Voxel-Arrays pro LOD
                                        finalChunk.getOrCreateLodArray(targetLod);

                                        // Vorübergehend das LOD für den Generator erzwingen
                                        int previousLod = finalChunk.getLod();
                                        finalChunk.setLod(targetLod);

                                        ChunkMeshGenerator.ChunkMeshResult result = ChunkMeshGenerator.generateMeshes(finalChunk, this);
                                        finalChunk.setSolidMesh(result.solidMesh());
                                        finalChunk.setWaterMesh(result.waterMesh());

                                        finalChunk.setLod(previousLod); // Zurücksetzen auf den Thread-Sicherheits-Status
                                        finalChunk.setReadyToUpload(true);
                                    } finally {
                                        chunksInGeneration.remove(genKey);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }

        // Chunks außerhalb des Radius entladen
        for (Map.Entry<Vector3i, VoxelChunk> entry : activeChunks.entrySet()) {
            Vector3i k = entry.getKey();
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

        List<VoxelChunk> pendingChunks = new ArrayList<>();
        for (VoxelChunk chunk : activeChunks.values()) {
            // Ein Chunk braucht einen GPU-Upload, wenn der Thread fertig ist, das aktuelle LOD aber noch nicht hochgeladen wurde
            if (chunk.isReadyToUpload() && !chunk.isFullyOccluded()) {
                pendingChunks.add(chunk);
            }
        }

        if (pendingChunks.isEmpty()) return;

        Vector3i dims = getChunkDimensions();
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

        for (VoxelChunk chunk : pendingChunks) {
            // ERKLÄRUNG: Da das rechenintensive Meshing jetzt komplett im Hintergrund-Thread stattfindet,
            // ruft diese Schleife hier nur noch die finale VAO/VBO-Erzeugung auf der GPU auf.
            // Angenommen, deine Mesh-Klasse hat eine Methode wie `.uploadToGPU()`.

            Mesh solid = chunk.getSolidMesh();
            Mesh water = chunk.getWaterMesh();

            if (solid != null && !solid.isUploaded()) {
                solid.uploadToGPU();
            }
            if (water != null && !water.isUploaded()) {
                water.uploadToGPU();
            }

            chunk.setReadyToUpload(false);

            uploadsThisFrame++;
            if (uploadsThisFrame >= maxUploadsPerFrame) {
                break;
            }
        }
    }

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
                if (chunk.isReadyToUpload() || chunk.getSolidMesh() != null) {
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

    public RaycastResult raycast(Vector3f rayOrigin, Vector3f rayDir, float maxDistance) {
        RaycastResult result = new RaycastResult();

        // Sicherheits-Check gegen Division durch Null
        float dx = (Math.abs(rayDir.x) < 1e-6f) ? 1e-6f : rayDir.x;
        float dy = (Math.abs(rayDir.y) < 1e-6f) ? 1e-6f : rayDir.y;
        float dz = (Math.abs(rayDir.z) < 1e-6f) ? 1e-6f : rayDir.z;

        // Start-Voxel (Abgerundete Kameraposition)
        int x = (int) Math.floor(rayOrigin.x);
        int y = (int) Math.floor(rayOrigin.y);
        int z = (int) Math.floor(rayOrigin.z);

        // Schrittrichtung auf den Achsen (1 oder -1)
        int stepX = (dx > 0) ? 1 : -1;
        int stepY = (dy > 0) ? 1 : -1;
        int stepZ = (dz > 0) ? 1 : -1;

        // Distanz bis zur nächsten Voxel-Kante auf jeder Achse
        float tMaxX = ((x + (dx > 0 ? 1 : 0)) - rayOrigin.x) / dx;
        float tMaxY = ((y + (dy > 0 ? 1 : 0)) - rayOrigin.y) / dy;
        float tMaxZ = ((z + (dz > 0 ? 1 : 0)) - rayOrigin.z) / dz;

        // Wie weit wandert der Strahl pro ganzem Voxel-Schritt
        float tDeltaX = Math.abs(1.0f / dx);
        float tDeltaY = Math.abs(1.0f / dy);
        float tDeltaZ = Math.abs(1.0f / dz);

        float t = 0.0f;
        int lastFace = -1; // Merkt sich das zuletzt durchschrittene Face

        Vector3i chunkDimensions = getChunkDimensions();
        Vector3i currentBlockPos = new Vector3i();
        Vector3i chunkPos = new Vector3i();
        Vector3i localPos = new Vector3i();

        // Voxel-Schleife (Wandert von Kante zu Kante)
        while (t <= maxDistance) {
            currentBlockPos.set(x, y, z);

            // Berechne die dazugehörigen Chunk-Koordinaten
            chunkPos.set(
                    (int) Math.floor((double) currentBlockPos.x / chunkDimensions.x),
                    (int) Math.floor((double) currentBlockPos.y / chunkDimensions.y),
                    (int) Math.floor((double) currentBlockPos.z / chunkDimensions.z)
            );

            VoxelChunk chunk = activeChunks.get(chunkPos);
            if (chunk != null) {
                // Lokale Koordinate innerhalb des Chunks ermitteln
                localPos.set(
                        currentBlockPos.x - (chunkPos.x * chunkDimensions.x),
                        currentBlockPos.y - (chunkPos.y * chunkDimensions.y),
                        currentBlockPos.z - (chunkPos.z * chunkDimensions.z)
                );

                byte blockId = chunk.getVoxel(localPos.x, localPos.y, localPos.z);

                // Treffer! Wenn der Voxel kein Luftblock (0) ist
                if (blockId != 0) {
                    result.hit = true;
                    result.blockPos.set(currentBlockPos);
                    result.face = lastFace;

                    // Berechne die Position direkt vor der getroffenen Wand
                    result.adjacentPos.set(currentBlockPos);
                    if (lastFace == 0) result.adjacentPos.z -= 1;  // Getroffen: Hinten (Z-) -> Davor ist Z-
                    if (lastFace == 1) result.adjacentPos.z += 1;  // Getroffen: Vorne  (Z+) -> Davor ist Z+
                    if (lastFace == 2) result.adjacentPos.x -= 1;  // Getroffen: Links  (X-) -> Davor ist X-
                    if (lastFace == 3) result.adjacentPos.x += 1;  // Getroffen: Rechts (X+) -> Davor ist X+
                    if (lastFace == 4) result.adjacentPos.y += 1;  // Getroffen: Oben   (Y+) -> Davor ist Y+
                    if (lastFace == 5) result.adjacentPos.y -= 1; // Getroffen: Unten  (Y-) -> Davor ist Y-

                    return result;
                }
            }

            // Der Amanatides-Woo Kern: Entscheide, welche Kante als nächstes getroffen wird
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    x += stepX;
                    lastFace = (stepX > 0) ? 2 : 3; // Links oder Rechts getroffen
                } else {
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    lastFace = (stepZ > 0) ? 0 : 1; // Hinten oder Vorne getroffen
                }
            } else {
                if (tMaxY < tMaxZ) {
                    t = tMaxY;
                    tMaxY += tDeltaY;
                    y += stepY;
                    lastFace = (stepY > 0) ? 5 : 4; // Unten oder Oben getroffen
                } else {
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    lastFace = (stepZ > 0) ? 0 : 1;
                }
            }
        }
        return result; // Nichts getroffen innerhalb von maxDistance
    }

    public void setVoxelAtWorldPos(Vector3i worldPos, byte newBlockId) {
        Vector3i dims = getChunkDimensions();

        // Bestimme den Ziel-Chunk
        Vector3i chunkPos = new Vector3i(
                (int) Math.floor((double) worldPos.x / dims.x),
                (int) Math.floor((double) worldPos.y / dims.y),
                (int) Math.floor((double) worldPos.z / dims.z)
        );

        VoxelChunk chunk = activeChunks.get(chunkPos);
        if (chunk != null) {
            // Lokale Position innerhalb des betroffenen Chunks berechnen
            int lx = worldPos.x - (chunkPos.x * dims.x);
            int ly = worldPos.y - (chunkPos.y * dims.y);
            int lz = worldPos.z - (chunkPos.z * dims.z);

            // 1. Wert im Daten-Array des Haupt-Chunks überschreiben
            chunk.setVoxel(lx, ly, lz, newBlockId);

            // 2. Altes GPU-Mesh des Haupt-Chunks löschen (RAM-Schonung)
            if (chunk.getSolidMesh() != null) chunk.getSolidMesh().cleanup();
            if (chunk.getWaterMesh() != null) chunk.getWaterMesh().cleanup();

            // 3. Mesh des Haupt-Chunks SOFORT auf der CPU neu kompilieren und zuweisen
            ChunkMeshGenerator.ChunkMeshResult newMeshes = ChunkMeshGenerator.generateMeshes(chunk, this);
            chunk.setSolidMesh(newMeshes.solidMesh());
            chunk.setWaterMesh(newMeshes.waterMesh());

            // =================================================================
            // NEU: CROSS-CHUNK RE-MESHING (NACHBARN AKTUALISIEREN)
            // =================================================================
            // Hilfs-Vektor für die Nachbar-Schlüssel
            Vector3i neighborKey = new Vector3i();

            // X-Grenzen prüfen
            if (lx == 0) {
                neighborKey.set(chunkPos.x - 1, chunkPos.y, chunkPos.z);
                rebuildChunkMeshImmediate(neighborKey);
            } else if (lx == dims.x - 1) {
                neighborKey.set(chunkPos.x + 1, chunkPos.y, chunkPos.z);
                rebuildChunkMeshImmediate(neighborKey);
            }

            // Y-Grenzen prüfen
            if (ly == 0) {
                neighborKey.set(chunkPos.x, chunkPos.y - 1, chunkPos.z);
                rebuildChunkMeshImmediate(neighborKey);
            } else if (ly == dims.y - 1) {
                neighborKey.set(chunkPos.x, chunkPos.y + 1, chunkPos.z);
                rebuildChunkMeshImmediate(neighborKey);
            }

            // Z-Grenzen prüfen
            if (lz == 0) {
                neighborKey.set(chunkPos.x, chunkPos.y, chunkPos.z - 1);
                rebuildChunkMeshImmediate(neighborKey);
            } else if (lz == dims.z - 1) {
                neighborKey.set(chunkPos.x, chunkPos.y, chunkPos.z + 1);
                rebuildChunkMeshImmediate(neighborKey);
            }
            // =================================================================
        }
    }

    // Kleine private Hilfsmethode, um doppelten Code zu vermeiden
    private void rebuildChunkMeshImmediate(Vector3i chunkPos) {
        VoxelChunk neighbor = activeChunks.get(chunkPos);
        if (neighbor != null) {
            // Alte Grafikpuffer von der GPU fegen, um Speicherlecks zu verhindern
            if (neighbor.getSolidMesh() != null) neighbor.getSolidMesh().cleanup();
            if (neighbor.getWaterMesh() != null) neighbor.getWaterMesh().cleanup();

            // Neu berechnen und sofort wieder binden
            ChunkMeshGenerator.ChunkMeshResult nResult = ChunkMeshGenerator.generateMeshes(neighbor, this);
            neighbor.setSolidMesh(nResult.solidMesh());
            neighbor.setWaterMesh(nResult.waterMesh());
        }
    }

    public Map<Vector3i, VoxelChunk> getActiveChunks() { return activeChunks; }
    public Vector3i getChunkDimensions() { return chunkDimensions; }
}
