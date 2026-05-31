package engine.main;

public class GameSettings {
    private GameSettings() {}

    public static int windowWidth = 1280;
    public static int windowHeight = 720;
    public static boolean vSync = false;

    public static int renderDistance = 8;

    public static float masterVolume = 0.8f;
    public static float musicVolume = 0.5f;
    public static float soundEffectsVolume = 1.0f;

    public static String worldSeed = "VoxelEngine";
    public static final int TICKS_PER_SECOND = 20;
}
