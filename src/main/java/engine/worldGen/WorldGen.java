package engine.worldGen;

import engine.world.VoxelChunk;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGen {
    public static final byte BLOCK_AIR = 0;
    public static final byte BLOCK_GRASS = 1;
    public static final byte BLOCK_DIRT = 2;
    public static final byte BLOCK_STONE = 3;
    public static final byte BLOCK_WATER = 4; // Optional: Falls du Wasser hinzufügen willst

    private static final int MAX_HEIGHT = 256;
    private static final int WATER_LEVEL = 62; // Höhe des Meeresspiegels
    private static final int BASE_HEIGHT = 64;  // Mittlere Höhe der Welt

    private final Noise noise;

    Vector3i chunkDimensions = new Vector3i(16);

    private static final int MAX_CACHE_SIZE = 1024;

    private final Map<Long, Integer> surfaceHeightCache =
            Collections.synchronizedMap(new LinkedHashMap<Long, Integer>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    private static final int NOISE_SAMPLE_STEP = 4;

    public WorldGen(long seed) {
        this.noise = new Noise(seed);
    }

    public VoxelChunk genChunkAt(org.joml.Vector3i chunkPos) {
        org.joml.Vector3i dims = this.chunkDimensions;
        VoxelChunk chunk = new VoxelChunk(dims, chunkPos);

        int worldXOffset = chunkPos.x * dims.x;
        int worldYOffset = chunkPos.y * dims.y;
        int worldZOffset = chunkPos.z * dims.z;

        boolean containsNonAir = false;

        // 1. DYNAMISCHES GRID-ARRAY ANLEGEN
        // Wir berechnen, wie viele Stützpunkte wir für die Breite des Chunks benötigen (+1 für die Endkante)
        int numSamplesX = (dims.x / NOISE_SAMPLE_STEP) + 1;
        int numSamplesZ = (dims.z / NOISE_SAMPLE_STEP) + 1;
        int[][] sampleGrid = new int[numSamplesX][numSamplesZ];

        // Gitter mit echten Noise-Höhen an den Stützpunkten befüllen
        for (int sx = 0; sx < numSamplesX; sx++) {
            int globalX = worldXOffset + (sx * NOISE_SAMPLE_STEP);

            for (int sz = 0; sz < numSamplesZ; sz++) {
                int globalZ = worldZOffset + (sz * NOISE_SAMPLE_STEP);
                sampleGrid[sx][sz] = calculateNoiseHeight(globalX, globalZ);
            }
        }

        // 2. SPALTENWISE VERARBEITUNG UND BI-LINEARE INTERPOLATION
        for (int x = 0; x < dims.x; x++) {
            int globalX = worldXOffset + x;

            // Bestimme, in welchem Sample-Quadrat wir uns befinden
            int sampleIdxX = x / NOISE_SAMPLE_STEP;
            // Lokaler Fortschritt innerhalb des aktuellen Quadrats (0.0 bis 1.0)
            float fx = (float) (x % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

            for (int z = 0; z < dims.z; z++) {
                int globalZ = worldZOffset + z;

                int sampleIdxZ = z / NOISE_SAMPLE_STEP;
                float fz = (float) (z % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

                // Die 4 relevanten Stützpunkte für das aktuelle Quadrat auslesen
                int h00 = sampleGrid[sampleIdxX    ][sampleIdxZ    ]; // Unten-Links
                int h10 = sampleGrid[sampleIdxX + 1][sampleIdxZ    ]; // Unten-Rechts
                int h01 = sampleGrid[sampleIdxX    ][sampleIdxZ + 1]; // Oben-Links
                int h11 = sampleGrid[sampleIdxX + 1][sampleIdxZ + 1]; // Oben-Rechts

                // Bi-lineare Interpolation
                float hBottom = h00 + fx * (h10 - h00);
                float hTop    = h01 + fx * (h11 - h01);
                float interpolatedHeightVal = hBottom + fz * (hTop - hBottom);
                int surfaceHeight = Math.round(interpolatedHeightVal);

                // Vertikale Y-Schleife (Befüllung)
                for (int y = 0; y < dims.y; y++) {
                    int globalY = worldYOffset + y;

                    if (globalY >= MAX_HEIGHT) {
                        continue;
                    }

                    byte blockId = determineBlockType(globalX, globalY, globalZ, surfaceHeight);

                    if (blockId != BLOCK_AIR) {
                        chunk.setVoxel(x, y, z, blockId);
                        containsNonAir = true;
                    }
                }
            }
        }

        chunk.setFullyOccluded(!containsNonAir);
        return chunk;
    }

    // =========================================================================
    // ISOLIERTE NOISE-BERECHNUNG (Ersetzt die alte getOrCreateSurfaceHeight-Logik)
    // =========================================================================
    private int calculateNoiseHeight(int globalX, int globalZ) {
        // Da wir nur noch 4-mal pro Chunk anklopfen, können wir das Caching über
        // den long-Key beibehalten, um Nachbar-Chunks perfekt zu bedienen!
        long columnKey = ((long) globalX << 32) | (globalZ & 0xFFFFFFFFL);

        Integer cachedHeight = surfaceHeightCache.get(columnKey);
        if (cachedHeight != null) {
            return cachedHeight;
        }

        // Reine mathematische FBM-Abfrage
        double continentalNoise = noise.openSimplex2DFbm(globalX * 0.001, globalZ * 0.001, 2, 2.0, 0.4);
        double detailNoise = noise.openSimplex2DFbm(globalX * 0.008, globalZ * 0.008, 4, 2.0, 0.45);

        double mountainHeight = (continentalNoise * 35.0) + (detailNoise * 20.0);
        double valleyHeight   = (continentalNoise * 15.0) + (detailNoise * 6.0);

        double t = (continentalNoise + 1.0) * 0.5;
        t = t * t * (3.0 - 2.0 * t);

        double finalHeightModifier = valleyHeight + t * (mountainHeight - valleyHeight);
        int calculatedHeight = BASE_HEIGHT + (int) Math.round(finalHeightModifier);

        surfaceHeightCache.put(columnKey, calculatedHeight);
        return calculatedHeight;
    }

    private byte determineBlockType(int globalX, int globalY, int globalZ, int surfaceHeight) {
        // A: Über der Erdoberfläche (Himmel oder Ozean-Wasser)
        if (globalY > surfaceHeight) {
            if (globalY <= WATER_LEVEL) {
                return BLOCK_WATER;
            }
            return BLOCK_AIR;
        }

        // B: Exakt auf der Erdoberfläche
        if (globalY == surfaceHeight) {
            return (globalY <= WATER_LEVEL + 1) ? BLOCK_DIRT : BLOCK_GRASS;
        }

        // C: Die Erdschicht direkt unter dem Oberflächenblock
        if (globalY > surfaceHeight - 4) {
            return BLOCK_DIRT;
        }

        // D: DIE TIEFE ERDKRUSTE (STEIN)
        // Standardmäßig Stein platzieren, es sei denn, eine Höhle schneidet ihn weg
//        if (isInsideCave(globalX, globalY, globalZ)) {
//            return BLOCK_AIR;
//        }

        return BLOCK_STONE;
    }

    private boolean isInsideCave(int globalX, int globalY, int globalZ) {
        double caveCenterHeight = -30.0;
        double caveZoneWidth = 150.0;

        double distanceToCenter = Math.abs(globalY - caveCenterHeight);
        double caveSizeFactor = 1.0 - (distanceToCenter / caveZoneWidth);

        if (caveSizeFactor <= 0.01) {
            return false;
        }

        // Vertikales Dämpfungsprofil per Smoothstep berechnen
        caveSizeFactor = caveSizeFactor * caveSizeFactor * (3.0 - 2.0 * caveSizeFactor);

        double scale3D = 0.022;
        double n3d = noise.openSimplex(globalX * scale3D, globalY * 0.015, globalZ * scale3D);
        double spaghettiNoise = Math.abs(n3d);

        double tunnelThickness = 0.01 + (caveSizeFactor * 0.06);
        return spaghettiNoise < tunnelThickness;
    }
}
