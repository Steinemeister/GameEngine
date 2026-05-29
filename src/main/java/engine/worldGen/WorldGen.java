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

                float deltaX = Math.abs(h10 - h00) / (float) NOISE_SAMPLE_STEP;
                float deltaZ = Math.abs(h01 - h00) / (float) NOISE_SAMPLE_STEP;
                // Ein Wert > 1.2 bedeutet einen Höhenunterschied von mehr als 1.2 Blöcken pro Block Distanz
                boolean isSteepCliff = (deltaX > 1.2f || deltaZ > 1.2f);

                for (int y = 0; y < dims.y; y++) {
                    int globalY = worldYOffset + y;

                    if (globalY >= MAX_HEIGHT) {
                        continue;
                    }

                    // Übergabe des Steilheits-Flags an die Blockbestimmung
                    byte blockId = determineBlockType(globalX, globalY, globalZ, surfaceHeight, isSteepCliff);

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

    private int calculateNoiseHeight(int globalX, int globalZ) {
        long columnKey = ((long) globalX << 32) | (globalZ & 0xFFFFFFFFL);

        Integer cachedHeight = surfaceHeightCache.get(columnKey);
        if (cachedHeight != null) {
            return cachedHeight;
        }

        // 1. DOMAIN WARPING (Koordinaten-Verschiebung)
        // Wir holen uns zwei separate Rauschwerte für die X- und Z-Achse
        double warpScale = 0.004; // Wie großflächig die Verzerrung ist
        double warpIntensity = 45.0; // Wie stark die Kanten verzerrt/übergehängt werden

        double warpX = noise.openSimplex2DFbm(globalX * warpScale, globalZ * warpScale, 2, 2.0, 0.5) * warpIntensity;
        double warpZ = noise.openSimplex2DFbm((globalX + 500) * warpScale, (globalZ + 500) * warpScale, 2, 2.0, 0.5) * warpIntensity;

        // Die neuen, verzerrten Koordinaten für die eigentliche Landschaft
        double distortedX = globalX + warpX;
        double distortedZ = globalZ + warpZ;

        // 2. EIGENTLICHE LANDSCHAFTS-BERECHNUNG (auf den verzerrten Koordinaten)
        // Grobe Landmassen (Flachland vs. Gebirge)
        double continental = noise.openSimplex2DFbm(distortedX * 0.0005, distortedZ * 0.0005, 3, 2.0, 0.4);

        // Rauheit/Erosions-Faktor
        double erosion = noise.openSimplex2DFbm(distortedX * 0.0015, distortedZ * 0.0015, 3, 2.0, 0.5);

        // Detail-Rauschen
        double detail = noise.openSimplex2DFbm(distortedX * 0.007, distortedZ * 0.007, 4, 2.0, 0.48);

        // Ridged-Noise für scharfe Bergrücken
        double mountainNoise = 1.0 - Math.abs(detail);
        mountainNoise = mountainNoise * mountainNoise;

        // Sanfter Übergang für die Kontinentalplatte
        double influence = (continental + 1.0) * 0.5;
        influence = influence * influence * (3.0 - 2.0 * influence);

        // Höhenberechnung kombiniert aus Basis, Erosion und Gebirge
        double baseValley = influence * 25.0 + detail * 4.0;
        double ruggedMountains = influence * 85.0 * (1.0 - Math.abs(erosion)) + (mountainNoise * 30.0);

        // Gewichtete Zusammenführung
        double finalHeightModifier = baseValley + influence * (ruggedMountains - baseValley);

        int calculatedHeight = BASE_HEIGHT + (int) Math.round(finalHeightModifier);

        // Schutz gegen Überschreiten der Weltgrenzen
        if (calculatedHeight >= MAX_HEIGHT) calculatedHeight = MAX_HEIGHT - 1;
        if (calculatedHeight < 1) calculatedHeight = 1;

        surfaceHeightCache.put(columnKey, calculatedHeight);
        return calculatedHeight;
    }

    private byte determineBlockType(int globalX, int globalY, int globalZ, int surfaceHeight, boolean isSteepCliff) {
        if (globalY > surfaceHeight) {
            if (globalY <= WATER_LEVEL) {
                return BLOCK_WATER;
            }
            return BLOCK_AIR;
        }

        // B: Höhlengenerierung (Zuerst prüfen, damit Höhlen die Oberfläche durchbrechen können)
        if (isInsideCave(globalX, globalY, globalZ)) {
            if (globalY <= WATER_LEVEL) {
                return BLOCK_WATER;
            }
            return BLOCK_AIR;
        }

        // C: Steile Klippen (Sofort Stein, keine Erde/Gras)
        if (isSteepCliff && globalY > WATER_LEVEL) {
            return BLOCK_STONE;
        }

        // D: Normale Erdoberfläche
        if (globalY == surfaceHeight) {
            if (globalY <= WATER_LEVEL + 2) {
                return BLOCK_DIRT;
            }
            return BLOCK_GRASS;
        }

        // E: Die Erdschicht unter der Oberfläche
        if (globalY > surfaceHeight - 4) {
            return BLOCK_DIRT;
        }

        // F: Tiefe Erdkruste
        return BLOCK_STONE;
    }

    private boolean isInsideCave(int globalX, int globalY, int globalZ) {
        if (globalY > 120 || globalY < 5) {
            return false;
        }

        // Frequenzen für die Tunnel-Breite und Kurven
        double scale3D = 0.025;

        // Zwei unabhängige 3D-Noise-Kanäle für die Verschnitt-Methode
        double nA = noise.openSimplex(globalX * scale3D, globalY * 0.035, globalZ * scale3D);
        double nB = noise.openSimplex((globalX + 1000) * scale3D, globalY * 0.035, (globalZ + 1000) * scale3D);

        // Schwellenwert: Je kleiner, desto dünner/seltener die Tunnel
        double threshold = 0.08;

        // Wenn beide Noises sehr nah bei 0 sind, kreuzen sie sich und bilden einen Tunnel
        boolean isTunnel = Math.abs(nA) < threshold && Math.abs(nB) < threshold;

        if (isTunnel) {
            // Große "Käselöcher" verhindern, indem wir sehr dicke Knotenpunkte kappen
            return Math.abs(nA) + Math.abs(nB) > 0.02;
        }

        return false;
    }
}
