package engine.worldGen;

import engine.world.VoxelChunk;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorldGen {
    public static final byte BLOCK_AIR = 0;
    public static final byte BLOCK_GRASS = 1;
    public static final byte BLOCK_DIRT = 2;
    public static final byte BLOCK_STONE = 3;
    public static final byte BLOCK_WATER = 4;

    // Welt-Konstanten
    private static final int MAX_HEIGHT = 256;
    private static final int WATER_LEVEL = 62;
    private static final int BASE_HEIGHT = 64;

    // Höhlen-Konstanten
    private static final int CAVE_MIN_HEIGHT = 5;
    private static final int CAVE_MAX_HEIGHT = 120;

    // Fliegende Inseln Konstanten
    private static final int ISLANDS_MIN_HEIGHT = 180;
    private static final int ISLANDS_THICKNESS = 15;

    private final Noise noise;
    private final Vector3i chunkDimensions = new Vector3i(16);

    // Cache für die berechneten Oberflächenhöhen des Hauptgeländes
    private static final int MAX_CACHE_SIZE = 1024;
    private final Map<Long, Integer> surfaceHeightCache =
            Collections.synchronizedMap(new LinkedHashMap<Long, Integer>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    // Schrittweite für die Gitter-Interpolation
    private static final int NOISE_SAMPLE_STEP = 4;

    public WorldGen(long seed) {
        this.noise = new Noise(seed);
    }

    // =========================================================================
    // CHUNK-GENERIERUNG MIT ZWEI-ZONEN-AUFTEILUNG (HAUPTERDE & HIMMEL)
    // =========================================================================
    public VoxelChunk genChunkAt(Vector3i chunkPos) {
        Vector3i dims = this.chunkDimensions;
        VoxelChunk chunk = new VoxelChunk(dims, chunkPos);

        int worldXOffset = chunkPos.x * dims.x;
        int worldYOffset = chunkPos.y * dims.y;
        int worldZOffset = chunkPos.z * dims.z;

        boolean containsNonAir = false;

        // 1. Gitter-Array für Stützpunkte anlegen (+1 für die Endkante)
        int numSamplesX = (dims.x / NOISE_SAMPLE_STEP) + 1;
        int numSamplesZ = (dims.z / NOISE_SAMPLE_STEP) + 1;
        int[][] sampleGrid = new int[numSamplesX][numSamplesZ];

        // Gitter mit echten Noise-Höhen befüllen
        for (int sx = 0; sx < numSamplesX; sx++) {
            int globalX = worldXOffset + (sx * NOISE_SAMPLE_STEP);

            for (int sz = 0; sz < numSamplesZ; sz++) {
                int globalZ = worldZOffset + (sz * NOISE_SAMPLE_STEP);
                sampleGrid[sx][sz] = calculateNoiseHeight(globalX, globalZ);
            }
        }

        // 2. Spaltenweise Verarbeitung und bi-lineare Interpolation
        for (int x = 0; x < dims.x; x++) {
            int globalX = worldXOffset + x;
            int sampleIdxX = x / NOISE_SAMPLE_STEP;
            float fx = (float) (x % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

            for (int z = 0; z < dims.z; z++) {
                int globalZ = worldZOffset + z;
                int sampleIdxZ = z / NOISE_SAMPLE_STEP;
                float fz = (float) (z % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

                // Die 4 relevanten Stützpunkte auslesen
                int h00 = sampleGrid[sampleIdxX][sampleIdxZ];
                int h10 = sampleGrid[sampleIdxX + 1][sampleIdxZ];
                int h01 = sampleGrid[sampleIdxX][sampleIdxZ + 1];
                int h11 = sampleGrid[sampleIdxX + 1][sampleIdxZ + 1];

                // Bi-lineare Interpolation der Höhe
                float hBottom = h00 + fx * (h10 - h00);
                float hTop = h01 + fx * (h11 - h01);
                float interpolatedHeightVal = hBottom + fz * (hTop - hBottom);
                int surfaceHeight = Math.round(interpolatedHeightVal);

                // Klippen-Berechnung: Ist der Hang des Hauptgeländes zu steil?
                float deltaX = Math.abs(h10 - h00) / (float) NOISE_SAMPLE_STEP;
                float deltaZ = Math.abs(h01 - h00) / (float) NOISE_SAMPLE_STEP;
                boolean isSteepCliff = (deltaX > 1.2f || deltaZ > 1.2f);

                // Vertikale Y-Schleife (Befüllung der Blöcke)
                for (int y = 0; y < dims.y; y++) {
                    int globalY = worldYOffset + y;

                    if (globalY >= MAX_HEIGHT) {
                        continue;
                    }

                    byte blockId;

                    // Abfrage aufteilen: Himmel (Inseln) oder Hauptgelände
                    if (globalY >= ISLANDS_MIN_HEIGHT) {
                        blockId = determineIslandBlockType(globalX, globalY, globalZ);
                    } else {
                        blockId = determineBlockType(globalX, globalY, globalZ, surfaceHeight, isSteepCliff);
                    }

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
    // NOISE-BERECHNUNG MIT COORD-WARPING FÜR ORGANISCHE ÜBERHÄNGE (HAUPTERDE)
    // =========================================================================
    private int calculateNoiseHeight(int globalX, int globalZ) {
        long columnKey = ((long) globalX << 32) | (globalZ & 0xFFFFFFFFL);

        Integer cachedHeight = surfaceHeightCache.get(columnKey);
        if (cachedHeight != null) {
            return cachedHeight;
        }

        // Domain Warping (Verzerrung der X/Z-Koordinaten)
        double warpScale = 0.004;
        double warpIntensity = 45.0;

        double warpX = noise.openSimplex2DFbm(globalX * warpScale, globalZ * warpScale, 2, 2.0, 0.5) * warpIntensity;
        double warpZ = noise.openSimplex2DFbm((globalX + 500) * warpScale, (globalZ + 500) * warpScale, 2, 2.0, 0.5) * warpIntensity;

        double distortedX = globalX + warpX;
        double distortedZ = globalZ + warpZ;

        // FBM-Abfragen auf den verzerrten Koordinaten
        double continental = noise.openSimplex2DFbm(distortedX * 0.0005, distortedZ * 0.0005, 3, 2.0, 0.4);
        double erosion = noise.openSimplex2DFbm(distortedX * 0.0015, distortedZ * 0.0015, 3, 2.0, 0.5);
        double detail = noise.openSimplex2DFbm(distortedX * 0.007, distortedZ * 0.007, 4, 2.0, 0.48);

        // Ridged-Noise für alpine Bergrücken
        double mountainNoise = 1.0 - Math.abs(detail);
        mountainNoise = mountainNoise * mountainNoise;

        // Sanfter Übergang für Gebirgs-Einfluss
        double influence = (continental + 1.0) * 0.5;
        influence = influence * influence * (3.0 - 2.0 * influence);

        // Täler und Berge berechnen
        double baseValley = influence * 25.0 + detail * 4.0;
        double ruggedMountains = influence * 85.0 * (1.0 - Math.abs(erosion)) + (mountainNoise * 30.0);

        // Interpolation zwischen Berg und Tal
        double finalHeightModifier = baseValley + influence * (ruggedMountains - baseValley);
        int calculatedHeight = BASE_HEIGHT + (int) Math.round(finalHeightModifier);

        // Limits absichern
        if (calculatedHeight >= MAX_HEIGHT) calculatedHeight = MAX_HEIGHT - 1;
        if (calculatedHeight < 1) calculatedHeight = 1;

        surfaceHeightCache.put(columnKey, calculatedHeight);
        return calculatedHeight;
    }

    // =========================================================================
    // LOGISCHE BLOCK-PLATZIERUNG FÜR DAS HAUPTGELÄNDE
    // =========================================================================
    private byte determineBlockType(int globalX, int globalY, int globalZ, int surfaceHeight, boolean isSteepCliff) {
        // A: Über der Erdoberfläche (Himmel oder Ozean-Wasser)
        if (globalY > surfaceHeight) {
            if (globalY <= WATER_LEVEL) {
                return BLOCK_WATER;
            }
            return BLOCK_AIR;
        }

        // B: Höhlengenerierung (Bleibt trocken, da direkt AIR zurückgegeben wird)
        if (isInsideCave(globalX, globalY, globalZ)) {
            return BLOCK_AIR;
        }

        // C: Steile Klippen (Felswände komplett aus Stein, kein Gras/Erde)
        if (isSteepCliff && globalY > WATER_LEVEL) {
            return BLOCK_STONE;
        }

        // D: Exakt auf der Erdoberfläche
        if (globalY == surfaceHeight) {
            if (globalY <= WATER_LEVEL + 2) {
                return BLOCK_DIRT; // Sand/Strand-Ersatz am Wasser
            }
            return BLOCK_GRASS;
        }

        // E: Die Erdschicht direkt unter dem Oberflächenblock
        if (globalY > surfaceHeight - 4) {
            return BLOCK_DIRT;
        }

        // F: Tiefe Erdkruste
        return BLOCK_STONE;
    }

    // =========================================================================
    // BLOCK-BESTIMMUNG FÜR ORGANISCHE, ZERFURTETE FLIEGENDE INSELN
    // =========================================================================
    private byte determineIslandBlockType(int globalX, int globalY, int globalZ) {
        // 1. Domain Warping für die Wände der Inseln (Verschiebung basierend auf Y)
        double wallWarpScale = 0.05;
        double wallWarpIntensity = 12.0;

        double warpX = noise.openSimplex(globalX * wallWarpScale, globalY * 0.04, globalZ * wallWarpScale) * wallWarpIntensity;
        double warpZ = noise.openSimplex((globalX + 800) * wallWarpScale, globalY * 0.04, (globalZ + 800) * wallWarpScale) * wallWarpIntensity;

        double warpedX = globalX + warpX;
        double warpedZ = globalZ + warpZ;
        // 2. Insel-Form bestimmen (auf den verzerrten Koordinaten)
        double islandNoise = noise.openSimplex2DFbm(warpedX * 0.006, warpedZ * 0.006, 3, 2.0, 0.55);

        // Lokale Oberkante der Insel ermitteln
        int islandTop = ISLANDS_MIN_HEIGHT + ISLANDS_THICKNESS + (int) Math.round(islandNoise * ISLANDS_THICKNESS);

        // 3. Keil-Effekt (Formt die Insel nach unten hin spitzer zu)
        int depthBelowTop = islandTop - globalY;
        double coneShrinkFactor = 0.04 * depthBelowTop;

        // 4. Zerfurchte Unterseite (3D-Noise für Felsstrukturen)
        double ruggedness = noise.openSimplex(warpedX * 0.04, globalY * 0.08, warpedZ * 0.04) * 5.0;

        // Finale Unterkante berechnenint
        int islandBottom = islandTop - (int) Math.round((islandNoise + 1.0) * ISLANDS_THICKNESS) + (int) Math.round(ruggedness);

        // Dynamischer Schwellenwert (wird nach unten hin strenger für die Kegelform)
        double densityThreshold = -0.1 + coneShrinkFactor;

        // Prüfen, ob der Block innerhalb der berechneten Schale liegt
        if (islandNoise > densityThreshold && globalY >= islandBottom && globalY <= islandTop) {
            if (globalY == islandTop) {
                return BLOCK_GRASS;
            }
            if (globalY > islandTop - 3) {
                return BLOCK_DIRT;
            }
            return BLOCK_STONE;
        }
        return BLOCK_AIR;
    }// =========================================================================

    // SPAGHETTI-HÖHLEN MIT AUSBLENDUNG AN BEIDEN GRENZEN (OBEN & UNTEN)
    // =========================================================================
    private boolean isInsideCave(int globalX, int globalY, int globalZ) {
        if (globalY > CAVE_MAX_HEIGHT || globalY < CAVE_MIN_HEIGHT) {
            return false;
        }
        // Sanftes Auslaufen (Fading) an den Rändern berechnen
        int fadeRange = 16;
        double fadeFactor = 1.0;
        if (globalY < CAVE_MIN_HEIGHT + fadeRange) {
            // Fading zur Untergrenze
            fadeFactor = (double) (globalY - CAVE_MIN_HEIGHT) / fadeRange;
        } else if (globalY > CAVE_MAX_HEIGHT - fadeRange) {
            // Fading zur Obergrenze
            fadeFactor = (double) (CAVE_MAX_HEIGHT - globalY) / fadeRange;
        }
        // Wert einmessen und Smoothstep anwenden
        fadeFactor = Math.max(0.0, Math.min(1.0, fadeFactor));
        fadeFactor = fadeFactor * fadeFactor * (3.0 - 2.0 * fadeFactor);

        double scale3D = 0.025;// Zwei phasenverschobene Rauschfelder verschneiden
        double nA = noise.openSimplex(globalX * scale3D, globalY * 0.035, globalZ * scale3D);
        double nB = noise.openSimplex((globalX + 1000) * scale3D, globalY * 0.035, (globalZ + 1000) * scale3D);

        double baseThreshold = 0.08;
        double dynamicThreshold = baseThreshold * fadeFactor;

        boolean isTunnel = Math.abs(nA) < dynamicThreshold && Math.abs(nB) < dynamicThreshold;
        if (isTunnel) {
            // Zu dicke Kreuzungspunkte ausdünnen
            return Math.abs(nA) + Math.abs(nB) > 0.02;
        }
        return false;
    }
}