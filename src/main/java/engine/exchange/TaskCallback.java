package engine.exchange;

@FunctionalInterface
public interface TaskCallback {
    void onComplete(EngineTask completetTask);
}
