package engine;

import logger.LoggerFactory;

public class Launcher {
    public static EngineManager engine;
    public static void main(String[] args) {
        LoggerFactory.init();
        engine = new EngineManager();
        engine.init();

    }
}
