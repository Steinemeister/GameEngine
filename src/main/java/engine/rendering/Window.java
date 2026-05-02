package engine.rendering;

import org.joml.Vector2f;

public class Window {
    private final long windowHandle;
    private Vector2f pos;
    private Vector2f size;

    public Window(Vector2f size, Vector2f pos, long windowHandle) {
        this.size = size;
        this.pos = pos;
        this.windowHandle = windowHandle;
    }


}
