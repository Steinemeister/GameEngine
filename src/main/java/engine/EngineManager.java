package engine;

import engine.rendering.RenderManager;
import logger.Logger;
import logger.LoggerFactory;

public class EngineManager {
    public static final Logger logger = LoggerFactory.getLogger("engineLogger");

    public RenderManager renderer;
    public Thread renderThread;

    public void init() {
        logger.info("starting engine");
        renderer = new RenderManager();
        renderThread = new Thread(renderer, "renderTread");
        renderThread.start();
    }
}
