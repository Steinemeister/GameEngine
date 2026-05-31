package engine.exchange;

import java.util.concurrent.ConcurrentLinkedQueue;

public class EngineTaskPool {
    private final ConcurrentLinkedQueue<EngineTask> pool = new ConcurrentLinkedQueue<>();

    public EngineTask obtain(TaskPriority priority, EngineData data, TaskCallback callback) {
        EngineTask task = pool.poll();
        if (task == null) {
            task = new EngineTask();
        }
        task.setup(priority, data, callback);
        return task;
    }

    public EngineTask obtain(TaskPriority priority, EngineData data) {
        return obtain(priority, data, null);
    }

    public void free(EngineTask task) {
        if (task == null) return;
        task.reset();
        pool.add(task);
    }
}
