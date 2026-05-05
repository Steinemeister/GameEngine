package engine.util;

import logger.Logger;
import logger.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class InputManager {
    private final Map<String, Integer> keyBindings = new HashMap<>();
    private final boolean[] keys = new boolean[GLFW_KEY_LAST];

    private double lastMouseX, lastMouseY;
    private float mouseDeltaX, mouseDeltaY;

    private boolean firstMouse = true;

    Logger logger;

    public InputManager(long window) {
        logger = LoggerFactory.getLogger("inputLogger for window: " + window);

        logger.info("initializing new inputManager");

        keyBindings.put("FORWARD", GLFW_KEY_W);
        keyBindings.put("BACKWARD", GLFW_KEY_S);
        keyBindings.put("LEFT", GLFW_KEY_A);
        keyBindings.put("RIGHT", GLFW_KEY_D);
        keyBindings.put("UP", GLFW_KEY_SPACE);
        keyBindings.put("DOWN", GLFW_KEY_LEFT_SHIFT);

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key >= 0 && key < keys.length) {
                keys[key] = (action != GLFW_RELEASE);
            }
        });

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }
            mouseDeltaX = (float) (xpos - lastMouseX);
            mouseDeltaY = (float) (ypos - lastMouseY);
            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    public boolean isActionActive(String action) {
        Integer keyCode = keyBindings.get(action);
        return keyCode != null && keys[keyCode];
    }

    public void remap(String action, int newKeyCode) {
        keyBindings.put(action, newKeyCode);
    }

    public float getMouseDeltaX() {
        float d = mouseDeltaX;
        mouseDeltaX = 0;
        return d;
    }
    public float getMouseDeltaY() {
        float d = mouseDeltaY;
        mouseDeltaY = 0;
        return d;
    }
}
