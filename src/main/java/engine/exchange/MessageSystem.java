package engine.exchange;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

public class MessageSystem {
    public enum ThreadType {
        MAIN,
        RENDER,
        SOUND,
        ASYNC_WORKER
    }

    private final PriorityBlockingQueue<EngineTask> mainQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<EngineTask> renderQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<EngineTask> soundQueue = new PriorityBlockingQueue<>();

    private final ExecutorService asyncWorkerPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    private final EngineTaskPool taskPool;

    public MessageSystem(EngineTaskPool taskPool) {
        this.taskPool = taskPool;
    }

    public void sendMessage(ThreadType target, EngineTask task) {
        if (task == null) return;

        switch (target) {
            case MAIN -> mainQueue.add(task);
            case RENDER -> renderQueue.add(task);
            case SOUND -> soundQueue.add(task);
            case ASYNC_WORKER -> {
                asyncWorkerPool.execute(() -> {
                    try {
                        task.executeCallback();
                    } finally {
                        taskPool.free(task);
                    }
                });
            }
        }
    }

    public void sendMessage(ThreadType target, Runnable action) {
        if (action == null) return;

        if (target == ThreadType.ASYNC_WORKER) {
            asyncWorkerPool.execute(action);
        } else {
            throw new IllegalArgumentException(
                    "Direkte Runnables werden nur für ASYNC_WORKER unterstützt. " +
                            "Nutze für Kontext-Threads bitte sendMessage(ThreadType, EngineTask)."
            );
        }
    }

    public void getTasks(ThreadType type, int count, List<EngineTask> targetList) {
        targetList.clear();
        PriorityBlockingQueue<EngineTask> queue = getQueue(type);

        EngineTask task;
        while (targetList.size() < count && (task = queue.poll()) != null) {
            targetList.add(task);
        }
    }

    public PriorityBlockingQueue<EngineTask> getQueue(ThreadType type) {
        return switch (type) {
            case MAIN -> mainQueue;
            case RENDER -> renderQueue;
            case SOUND -> soundQueue;
            case ASYNC_WORKER -> throw new IllegalArgumentException("ASYNC_WORKER besitzt keine getTasks() Warteschlange!");
        };
    }

    public void shutdown() {
        asyncWorkerPool.shutdownNow();
    }
}
