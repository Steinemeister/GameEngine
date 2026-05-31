package engine.exchange;

public enum TaskPriority {
    CRITICAL(1000),
    HIGH(500),
    NORMAL(100),
    LOW(10),
    IDLE(0);

    private final int value;

    TaskPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
