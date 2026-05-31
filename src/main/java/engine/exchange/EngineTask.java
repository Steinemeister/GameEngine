package engine.exchange;

import java.util.concurrent.atomic.AtomicLong;

public class EngineTask implements Comparable<EngineTask> {
    private static final AtomicLong seq = new AtomicLong(0);

    private long sequenceNumber;
    private TaskPriority priority;
    private EngineData data;
    private TaskCallback callback;

    public EngineTask() {
        this.sequenceNumber = seq.getAndIncrement();
    }

    public void setup(TaskPriority priority, EngineData data, TaskCallback callback) {
        this.priority = priority;
        this.data = data;
        this.callback = callback;
        this.sequenceNumber = seq.getAndIncrement();
    }

    public void executeCallback() {
        if (callback != null) {
            try {
                callback.onComplete(this);
            } catch (Exception e) {
                System.err.println("Fehler im Task-Callback: " + e.getMessage());
            }
        }
    }

    public void reset() {
        this.data = null;
        this.callback = null;
        this.priority = null; // Referenz löschen für den GC
    }

    public TaskPriority getPriority() { return priority; }
    public EngineData getData() { return data; }

    @Override
    public int compareTo(EngineTask other) {
        // Wir vergleichen die numerischen Werte der beiden Enums (Absteigend)
        int res = Integer.compare(other.priority.getValue(), this.priority.getValue());
        if (res == 0) {
            // FIFO bei exakt gleicher Priorität
            return Long.compare(this.sequenceNumber, other.sequenceNumber);
        }
        return res;
    }
}
