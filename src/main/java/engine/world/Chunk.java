package engine.world;

import engine.rendering.ChunkMesh;

public class Chunk {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 16;
    public static final int LENGTH = 16;

    private final int chunkX;
    private final int chunkZ;
    private final byte[][][] blockData;

    // Das dazugehörige Grafik-Mesh (null, wenn noch nicht im VRAM geladen)
    private ChunkMesh mesh;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockData = new byte[WIDTH][HEIGHT][LENGTH];
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public byte[][][] getBlockData() { return blockData; }

    public byte getBlock(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= LENGTH) return 0;
        return blockData[x][y][z];
    }

    public void setBlock(int x, int y, int z, byte blockType) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < LENGTH) {
            blockData[x][y][z] = blockType;
        }
    }

    public ChunkMesh getMesh() { return mesh; }
    public void setMesh(ChunkMesh mesh) { this.mesh = mesh; }
}
