package engine.exchange;

public interface EngineData {

    public record LoadChunkData(
            int chunkX,
            int chunkZ,
            byte[][][] blockData
    ) implements EngineData {}

    public record LoadTextureRequest(
            String filePath,
            int textureSlot
    ) implements EngineData {}

    public record DeleteBufferData(
            int bufferId
    ) implements EngineData {}

    public record LoadTextureResponse(
            String filePath,
            int openGLTextureId
    ) implements EngineData {}

    public record PlayerJumpInput(
            float force
    ) implements EngineData {}

    public record EnemySpawned(
            float x,
            float y,
            int enemyType
    ) implements EngineData {}

    public record PlaySoundData(
            String filePath,
            float volume,
            boolean loop
    ) implements EngineData {}

    public record PlaySoundEffect(
            String soundPath,
            float volume
    ) implements EngineData {}

    public record StopAllAudioData() implements EngineData {}

    public record GenerateChunkNoiseData(
            int x,
            int z
    ) implements EngineData {}

    public record MoveCameraData(float moveX, float moveZ) implements EngineData {}
}
