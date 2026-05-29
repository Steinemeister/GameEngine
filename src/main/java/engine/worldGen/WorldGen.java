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

    private static final int WATER_LEVEL = 62;
    private static final int BASE_HEIGHT = 64;

    private static final int CAVE_MIN_HEIGHT = 5;
    private static final int CAVE_MAX_HEIGHT = 120;

    private final Noise noise;
    private final Vector3i chunkDimensions = new Vector3i(16);

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

    public VoxelChunk genChunkAt(Vector3i chunkPos) {
        Vector3i dims = this.chunkDimensions;
        VoxelChunk chunk = new VoxelChunk(dims, chunkPos);

        int worldXOffset = chunkPos.x << 4;
        int worldYOffset = chunkPos.y << 4;
        int worldZOffset = chunkPos.z << 4;

        int numSamplesX = (16 / NOISE_SAMPLE_STEP) + 1;
        int numSamplesZ = (16 / NOISE_SAMPLE_STEP) + 1;
        int[][] sampleGrid = new int[numSamplesX][numSamplesZ];

        int minGridHeight = Integer.MAX_VALUE;
        int maxGridHeight = Integer.MIN_VALUE;

        for (int sx = 0; sx < numSamplesX; sx++) {
            int globalX = worldXOffset + (sx * NOISE_SAMPLE_STEP);
            for (int sz = 0; sz < numSamplesZ; sz++) {
                int globalZ = worldZOffset + (sz * NOISE_SAMPLE_STEP);
                int h = calculateNoiseHeight(globalX, globalZ);
                sampleGrid[sx][sz] = h;

                if (h < minGridHeight) minGridHeight = h;
                if (h > maxGridHeight) maxGridHeight = h;
            }
        }

        minGridHeight -= 4;
        maxGridHeight += 4;

        if (worldYOffset + 16 <= minGridHeight && worldYOffset + 16 < CAVE_MIN_HEIGHT) {
            chunk.fill(BLOCK_STONE);
            chunk.setFullyOccluded(false);
            return chunk;
        }

        if (worldYOffset > maxGridHeight && worldYOffset > WATER_LEVEL) {
            chunk.fill(BLOCK_AIR);
            chunk.setFullyOccluded(true);
            return chunk;
        }

        float[][][] caveDensityGrid = null;
        boolean chunkHasCaves = worldYOffset <= CAVE_MAX_HEIGHT && worldYOffset + 16 >= CAVE_MIN_HEIGHT;

        if (chunkHasCaves) {
            int numSamplesY = (16 / NOISE_SAMPLE_STEP) + 1;
            caveDensityGrid = new float[numSamplesX][numSamplesY][numSamplesZ];
            for (int sx = 0; sx < numSamplesX; sx++) {
                int globalX = worldXOffset + (sx * NOISE_SAMPLE_STEP);
                for (int sy = 0; sy < numSamplesY; sy++) {
                    int globalY = worldYOffset + (sy * NOISE_SAMPLE_STEP);
                    for (int sz = 0; sz < numSamplesZ; sz++) {
                        int globalZSample = worldZOffset + (sz * NOISE_SAMPLE_STEP);
                        caveDensityGrid[sx][sy][sz] = getCaveDensity(globalX, globalY, globalZSample);
                    }
                }
            }
        }

        boolean containsNonAir = false;

        for (int x = 0; x < 16; x++) {
            int sampleIdxX = x / NOISE_SAMPLE_STEP;
            float fx = (float) (x % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

            for (int z = 0; z < 16; z++) {
                int sampleIdxZ = z / NOISE_SAMPLE_STEP;
                float fz = (float) (z % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

                int h00 = sampleGrid[sampleIdxX    ][sampleIdxZ    ];
                int h10 = sampleGrid[sampleIdxX + 1][sampleIdxZ    ];
                int h01 = sampleGrid[sampleIdxX    ][sampleIdxZ + 1];
                int h11 = sampleGrid[sampleIdxX + 1][sampleIdxZ + 1];

                float hBottom = h00 + fx * (h10 - h00);
                float hTop    = h01 + fx * (h11 - h01);
                float interpolatedHeightVal = hBottom + fz * (hTop - hBottom);
                int surfaceHeight = Math.round(interpolatedHeightVal);

                // Klippen-Neigung direkt mathematisch sauber aus den 4 Eckpunkten ableiten
                float slopeX = Math.abs((h10 - h00) * (1.0f - fz) + (h11 - h01) * fz) / (float) NOISE_SAMPLE_STEP;
                float slopeZ = Math.abs((h01 - h00) * (1.0f - fx) + (h11 - h10) * fx) / (float) NOISE_SAMPLE_STEP;
                boolean isSteepCliff = (slopeX > 1.2f || slopeZ > 1.2f);

                int relSurfaceHeight = surfaceHeight - worldYOffset;
                int relCaveMin = CAVE_MIN_HEIGHT - worldYOffset;

                int stoneEnd = Math.max(0, Math.min(16, Math.min(relSurfaceHeight - 4, relCaveMin)));
                if (stoneEnd > 0) {
                    chunk.fillColumn(x, z, 0, stoneEnd, BLOCK_STONE);
                    containsNonAir = true;
                }

                int startY = stoneEnd;
                int endY = Math.max(0, Math.min(16, relSurfaceHeight + 1));

                for (int y = startY; y < endY; y++) {
                    int globalY = worldYOffset + y;

                    boolean isCave = false;
                    if (chunkHasCaves && globalY >= CAVE_MIN_HEIGHT && globalY <= CAVE_MAX_HEIGHT) {
                        int sampleIdxY = y / NOISE_SAMPLE_STEP;
                        float fy = (float) (y % NOISE_SAMPLE_STEP) / NOISE_SAMPLE_STEP;

                        float d000 = caveDensityGrid[sampleIdxX    ][sampleIdxY    ][sampleIdxZ    ];
                        float d100 = caveDensityGrid[sampleIdxX + 1][sampleIdxY    ][sampleIdxZ    ];
                        float d010 = caveDensityGrid[sampleIdxX    ][sampleIdxY + 1][sampleIdxZ    ];
                        float d110 = caveDensityGrid[sampleIdxX + 1][sampleIdxY + 1][sampleIdxZ    ];
                        float d001 = caveDensityGrid[sampleIdxX    ][sampleIdxY    ][sampleIdxZ + 1];
                        float d101 = caveDensityGrid[sampleIdxX + 1][sampleIdxY    ][sampleIdxZ + 1];
                        float d011 = caveDensityGrid[sampleIdxX    ][sampleIdxY + 1][sampleIdxZ + 1];
                        float d111 = caveDensityGrid[sampleIdxX + 1][sampleIdxY + 1][sampleIdxZ + 1];

                        float x00 = d000 + fx * (d100 - d000);
                        float x10 = d010 + fx * (d110 - d010);
                        float x01 = d001 + fx * (d101 - d001);
                        float x11 = d011 + fx * (d111 - d011);
                        float r0 = x00 + fy * (x10 - x00);
                        float r1 = x01 + fy * (x11 - x01);

                        isCave = (r0 + fz * (r1 - r0)) > 0.0f;
                    }

                    int globalX = worldXOffset + x;
                    int globalZ = worldZOffset + z;
                    byte blockId = determineBlockTypeFast(globalX, globalY, surfaceHeight, isSteepCliff, isCave);

                    if (blockId != BLOCK_AIR) {
                        chunk.setVoxel(x, y, z, blockId);
                        containsNonAir = true;
                    }
                }

                int waterStart = Math.max(0, Math.min(16, relSurfaceHeight + 1));
                int waterEnd = Math.max(0, Math.min(16, (WATER_LEVEL + 1) - worldYOffset));
                if (waterStart < waterEnd) {
                    chunk.fillColumn(x, z, waterStart, waterEnd, BLOCK_WATER);
                    containsNonAir = true;
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

        double warpScale = 0.004;
        double warpIntensity = 45.0;

        double warpX = noise.openSimplex2DFbm(globalX * warpScale, globalZ * warpScale, 2, 2.0, 0.5) * warpIntensity;
        double warpZ = noise.openSimplex2DFbm((globalX + 500) * warpScale, (globalZ + 500) * warpScale, 2, 2.0, 0.5) * warpIntensity;

        double distortedX = globalX + warpX;
        double distortedZ = globalZ + warpZ;

        double superContinental = noise.openSimplex2DFbm(distortedX * 0.0001, distortedZ * 0.0001, 2, 2.0, 0.35);
        double continental = noise.openSimplex2DFbm(distortedX * 0.0005, distortedZ * 0.0005, 3, 2.0, 0.4);
        double erosion = noise.openSimplex2DFbm(distortedX * 0.0015, distortedZ * 0.0015, 3, 2.0, 0.5);
        double detail = noise.openSimplex2DFbm(distortedX * 0.007, distortedZ * 0.007, 4, 2.0, 0.48);

        double mountainNoise = 1.0 - Math.abs(detail);
        mountainNoise = mountainNoise * mountainNoise;

        double globalHeightMultiplier = (superContinental + 1.0) * 0.5;
        globalHeightMultiplier = globalHeightMultiplier * globalHeightMultiplier * (3.0 - 2.0 * globalHeightMultiplier);

        double influence = (continental + 1.0) * 0.5;
        influence = influence * influence * (3.0 - 2.0 * influence);

        double baseValley = influence * 25.0 + detail * 4.0;
        double ruggedMountains = influence * 85.0 * (1.0 - Math.abs(erosion)) + (mountainNoise * 30.0);

        double combinedHeight = baseValley + influence * (ruggedMountains - baseValley);

        double finalHeightModifier = combinedHeight * (0.4 + globalHeightMultiplier * 1.4);
        int calculatedHeight = BASE_HEIGHT + (int) Math.round(finalHeightModifier);

        if (calculatedHeight < 1) calculatedHeight = 1;

        surfaceHeightCache.put(columnKey, calculatedHeight);
        return calculatedHeight;
    }

    private boolean isInsideCave(int globalX, int globalY, int globalZ) {
        if (globalY > CAVE_MAX_HEIGHT || globalY < CAVE_MIN_HEIGHT) {
            return false;
        }

        int fadeRange = 16;
        double fadeFactor = 1.0;

        if (globalY < CAVE_MIN_HEIGHT + fadeRange) {
            fadeFactor = (double) (globalY - CAVE_MIN_HEIGHT) / fadeRange;
        } else if (globalY > CAVE_MAX_HEIGHT - fadeRange) {
            fadeFactor = (double) (CAVE_MAX_HEIGHT - globalY) / fadeRange;
        }

        fadeFactor = Math.max(0.0, Math.min(1.0, fadeFactor));
        fadeFactor = fadeFactor * fadeFactor * (3.0 - 2.0 * fadeFactor);

        double scale3D = 0.025;
        double nA = noise.openSimplex(globalX * scale3D, globalY * 0.035, globalZ * scale3D);
        double nB = noise.openSimplex((globalX + 1000) * scale3D, globalY * 0.035, (globalZ + 1000) * scale3D);

        double baseThreshold = 0.08;
        double dynamicThreshold = baseThreshold * fadeFactor;

        boolean isTunnel = Math.abs(nA) < dynamicThreshold && Math.abs(nB) < dynamicThreshold;

        if (isTunnel) {
            return Math.abs(nA) + Math.abs(nB) > 0.02;
        }

        return false;
    }

    private float getCaveDensity(int globalX, int globalY, int globalZ) {
        if (globalY > CAVE_MAX_HEIGHT || globalY < CAVE_MIN_HEIGHT) {
            return -1.0f;
        }

        int fadeRange = 16;
        double fadeFactor = 1.0;

        if (globalY < CAVE_MIN_HEIGHT + fadeRange) {
            fadeFactor = (double) (globalY - CAVE_MIN_HEIGHT) / fadeRange;
        } else if (globalY > CAVE_MAX_HEIGHT - fadeRange) {
            fadeFactor = (double) (CAVE_MAX_HEIGHT - globalY) / fadeRange;
        }

        fadeFactor = Math.max(0.0, Math.min(1.0, fadeFactor));
        fadeFactor = fadeFactor * fadeFactor * (3.0 - 2.0 * fadeFactor);

        double scale3D = 0.025;
        double nA = noise.openSimplex(globalX * scale3D, globalY * 0.035, globalZ * scale3D);
        double nB = noise.openSimplex((globalX + 1000) * scale3D, globalY * 0.035, (globalZ + 1000) * scale3D);

        double baseThreshold = 0.08;
        double dynamicThreshold = baseThreshold * fadeFactor;

        // Wenn wir innerhalb der Tunnel-Grenzen sind, geben wir einen positiven Wert zurück (Dichte vorhanden -> Höhle!)
        if (Math.abs(nA) < dynamicThreshold && Math.abs(nB) < dynamicThreshold) {
            if (Math.abs(nA) + Math.abs(nB) > 0.02) {
                return 1.0f;
            }
        }
        return -1.0f;
    }

    private byte determineBlockTypeFast(int globalX, int globalY, int surfaceHeight, boolean isSteepCliff, boolean isCave) {
        if (globalY > surfaceHeight) {
            if (globalY <= WATER_LEVEL) return BLOCK_WATER;
            return BLOCK_AIR;
        }

        // Höhle direkt mit dem vorberechneten boolean abfangen
        if (isCave) {
            return BLOCK_AIR;
        }

        if (isSteepCliff && globalY > WATER_LEVEL) {
            return BLOCK_STONE;
        }

        if (globalY == surfaceHeight) {
            if (globalY <= WATER_LEVEL + 2) return BLOCK_DIRT;
            return BLOCK_GRASS;
        }

        if (globalY > surfaceHeight - 4) {
            return BLOCK_DIRT;
        }

        return BLOCK_STONE;
    }
}