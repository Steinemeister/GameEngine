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
     * Erstellt einen Chunk an der Grid-Position (cx, cy, cz), falls er noch nicht existiert.
     */
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
     * Entlädt einen Chunk und gibt seine GPU-Texturen frei.
     */
    public void unloadChunk(int cx, int cy, int cz) {
        Vector3i key = new Vector3i(cx, cy, cz);
        VoxelChunk chunk = activeChunks.remove(key);
        if (chunk != null) {
            chunk.cleanup();
        }
    }

    /**
     * Setzt eine Block-ID über globale Weltkoordinaten und aktualisiert die GPU-Textur sofort.
     */
    public void setVoxelAtWorld(Vector3f worldPos, byte blockId) {
        Vector3i chunkKey = new Vector3i();
        VoxelChunk.getChunkPositionFromWorld(worldPos, chunkDimensions, chunkKey);
        VoxelChunk chunk = activeChunks.get(chunkKey);

        if (chunk != null) {
            Vector3i localPos = new Vector3i();
            chunk.worldToLocalCoordinate(worldPos, localPos);
            chunk.setVoxel(localPos.x, localPos.y, localPos.z, blockId);

            // WICHTIG: Die 3D-Textur des Chunks neu auf die GPU hochladen
            chunk.update3DTexture();
        }
    }

    /**
     * Gibt die Block-ID an einer globalen Weltkoordinate zurück.
     * Gibt 0 (Luft) zurück, falls der betroffene Chunk nicht geladen ist.
     */
    public byte getVoxelAtWorld(Vector3f worldPos) {
        Vector3i chunkKey = new Vector3i();
        VoxelChunk.getChunkPositionFromWorld(worldPos, chunkDimensions, chunkKey);
        VoxelChunk chunk = activeChunks.get(chunkKey);

        if (chunk != null) {
            Vector3i localPos = new Vector3i();
            chunk.worldToLocalCoordinate(worldPos, localPos);
            return chunk.getVoxel(localPos.x, localPos.y, localPos.z);
        }
        return 0; // Luft als Fallback
    }

    /**
     * Interaktions-Methode: Ändert einen Voxel und zwingt die GPU zur Textur-Aktualisierung.
     * (Ersetzt die alte setVoxelAndRefresh-Methode)
     */
    public void setVoxelAndRefresh(Vector3f worldPos, byte blockId) {
        setVoxelAtWorld(worldPos, blockId);
    }

    /**
     * Generiert die initialen Dummy-Meshes und lädt die 3D-Texturen für alle Chunks hoch.
     */
    public void generateAllMeshes() {
        for (VoxelChunk chunk : activeChunks.values()) {
            // Generiert das Dummy-Mesh (Sagt der GPU nur, wie viele Vertices simuliert werden sollen)
            Mesh dummyMesh = ChunkMeshGenerator.generateMesh(chunk, this);
            chunk.setMesh(dummyMesh);

            // Übermittelt das Voxel-Byte-Array als 3D-Textur an die Grafikkarte
            chunk.update3DTexture();
        }
    }

    /**
     * Dynamisches Laden/Entladen von Chunks im Radius um eine Weltposition (z.B. den Spieler).
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
                        chunk = new VoxelChunk(chunkDimensions, chunkKey);
                    }
                    newActiveChunks.put(chunkKey, chunk);
                }
            }
        }

        // Alle Chunks, die aus dem Sichtradius fliegen, sauber löschen (GPU-Speicher freigeben!)
        for (Map.Entry<Vector3i, VoxelChunk> entry : activeChunks.entrySet()) {
            if (!newActiveChunks.containsKey(entry.getKey())) {
                entry.getValue().cleanup();
            }
        }

        activeChunks.clear();
        activeChunks.putAll(newActiveChunks);
    }

    /**
     * Gibt alle nativen Texturen und VAOs der Chunks im Grafikspeicher frei.
     */
    public void cleanup() {
        for (VoxelChunk chunk : activeChunks.values()) {
            chunk.cleanup();
        }
        activeChunks.clear();
    }

    // --- Hilfsklasse für den Strahlentest (Raycasting) ---
    public record RaycastResult(Vector3i blockPos, Vector3i faceNormal) {}

    /**
     * DDA-Algorithmus zur Erkennung, welcher Block anvisiert wird.
     */
    public RaycastResult raycast(Vector3f origin, Vector3f direction, float maxDistance) {
        Vector3f dir = new Vector3f(direction).normalize();

        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = (dir.x > 0) ? 1 : ((dir.x < 0) ? -1 : 0);
        int stepY = (dir.y > 0) ? 1 : ((dir.y < 0) ? -1 : 0);
        int stepZ = (dir.z > 0) ? 1 : ((dir.z < 0) ? -1 : 0);

        float tMaxX = (stepX > 0) ? (float)(Math.floor(origin.x) + 1 - origin.x) / dir.x : (float)(origin.x - Math.floor(origin.x)) / -dir.x;
        float tMaxY = (stepY > 0) ? (float)(Math.floor(origin.y) + 1 - origin.y) / dir.y : (float)(origin.y - Math.floor(origin.y)) / -dir.y;
        float tMaxZ = (stepZ > 0) ? (float)(Math.floor(origin.z) + 1 - origin.z) / dir.z : (float)(origin.z - Math.floor(origin.z)) / -dir.z;

        float tDeltaX = (stepX != 0) ? 1.0f / Math.abs(dir.x) : Float.MAX_VALUE;
        float tDeltaY = (stepY != 0) ? 1.0f / Math.abs(dir.y) : Float.MAX_VALUE;
        float tDeltaZ = (stepZ != 0) ? 1.0f / Math.abs(dir.z) : Float.MAX_VALUE;

        Vector3f currentWorldPos = new Vector3f();
        Vector3i lastFaceNormal = new Vector3i(0, 0, 0);

        float t = 0;
        while (t < maxDistance) {
            currentWorldPos.set(x, y, z);

            // Wenn die ID an dieser Position ungleich 0 (Luft) ist, haben wir einen Block getroffen!
            if (getVoxelAtWorld(currentWorldPos) > 0) {
                return new RaycastResult(new Vector3i(x, y, z), lastFaceNormal);
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    x += stepX;
                    lastFaceNormal.set(-stepX, 0, 0);
                } else {
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    lastFaceNormal.set(0, 0, -stepZ);
                }
            } else {
                if (tMaxY < tMaxZ) {
                    t = tMaxY;
                    tMaxY += tDeltaY;
                    y += stepY;
                    lastFaceNormal.set(0, -stepY, 0);
                } else {
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    lastFaceNormal.set(0, 0, -stepZ);
                }
            }
        }
        return null;
    }

    public void generateTerrainForChunk(VoxelChunk chunk) {
        org.joml.Vector3i chunkPos = chunk.getChunkPosition();
        org.joml.Vector3i dims = chunk.getDimensions();

        // Welt-Offsets berechnen
        int worldXOffset = chunkPos.x * dims.x;
        int worldZOffset = chunkPos.z * dims.z;
        int worldYOffset = chunkPos.y * dims.y;

        // Einstellungen für den Noise
        float frequency = 0.03f; // Höher = mehr Hügel auf engem Raum, Niedriger = flachere, weite Landschaft
        float maxMountainHeight = 24.0f; // Maximale Höhe der Berge in Blöcken
        float baseWaterLevel = 4.0f;     // Eine flache Mindesthöhe des Bodens

        for (int x = 0; x < dims.x; x++) {
            for (int z = 0; z < dims.z; z++) {
                // Globale Weltkoordinaten auf der horizontalen Ebene bestimmen
                float globalX = worldXOffset + x;
                float globalZ = worldZOffset + z;

                float noiseVal = PerlinNoiseGenerator.noise2D(globalX * frequency, globalZ * frequency);
                int targetHeight = (int) (baseWaterLevel + ((noiseVal + 1.0f) * 0.5f) * maxMountainHeight);

                for (int y = 0; y < dims.y; y++) {
                    int globalY = worldYOffset + y;

                    byte blockId = 0;

                    if (globalY <= targetHeight) {
                        if (globalY == targetHeight) {
                            blockId = 1; // Oberste Schicht: Gras (Kachel 03_)
                        } else if (globalY > targetHeight - 4) {
                            blockId = 2; // Die nächsten 3 Schichten darunter: Erde (Kachel 02_)
                        } else {
                            blockId = 3; // Alles tief im Boden: Stein (Kachel 01_)
                        }
                    }

                    chunk.setVoxel(x, y, z, blockId);
                }
            }
        }
    }

    // --- Getters ---
    public Map<Vector3i, VoxelChunk> getActiveChunks() { return activeChunks; }
    public Vector3i getChunkDimensions() { return chunkDimensions; }
}
