package engine.util;

import engine.world.Level;
import logger.Logger;
import logger.LoggerFactory;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class InputManager {
    private final long window;

    private double lastMouseX, lastMouseY;
    private float mouseXDelta, mouseYDelta;
    private boolean firstMouse = true;

    // --- Physikalische Konstanten ---
    private final float movementSpeed = 4.0f;     // Gehgeschwindigkeit (etwas langsamer als freies Fliegen)
    private final float mouseSensitivity = 0.15f;
    private final float playerRadius = 0.3f;
    private final float playerHeight = 1.8f;      // Augenhöhe des Spielers

    // --- NEU: Schwerkraft-Variablen ---
    private final float gravity = -28.0f;         // Fallbeschleunigung (Blöcke pro Quadratsekunde)
    private final float jumpForce = 8.0f;         // Sprungkraft nach oben
    private float verticalVelocity = 0.0f;        // Aktuelle Fall-/Steiggeschwindigkeit auf der Y-Achse
    private boolean isGrounded = false;           // Steht der Spieler aktuell fest auf dem Boden?

    private final Vector3f velocity = new Vector3f();
    private boolean collisionEnabled = true;

    private boolean vKeyWasPressed = false;

    private double lastBreakTime = 0.0;
    private double lastPlaceTime = 0.0;
    private static final double BREAK_COOLDOWN = 0.25; // 250 Millisekunden Sperre fürs Abbauen
    private static final double PLACE_COOLDOWN = 0.20;

    private RaycastResult lastRaycastResult = new RaycastResult();

    public InputManager(long window) {
        this.window = window;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    public void updateMouse() {
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(window, x, y);

        if (firstMouse) {
            lastMouseX = x[0];
            lastMouseY = y[0];
            firstMouse = false;
        }

        mouseXDelta = (float) (x[0] - lastMouseX);
        mouseYDelta = (float) (y[0] - lastMouseY);

        lastMouseX = x[0];
        lastMouseY = y[0];
    }

    public void handleBlockInteraction(Level level, Camera camera) {
        double currentTime = org.lwjgl.glfw.GLFW.glfwGetTime();

        // Führe das Raycasting in jedem Frame aus, um die Auswahlbox aktuell zu halten
        lastRaycastResult = level.raycast(camera.getPosition(), camera.getFront(), 6.0f);

        if (!lastRaycastResult.hit) return;

        // Abbauen (Linksklick) - Funktioniert nur nach Ablauf des Cooldowns
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            if (currentTime - lastBreakTime >= BREAK_COOLDOWN) {
                level.setVoxelAtWorldPos(lastRaycastResult.blockPos, (byte) 0);
                lastBreakTime = currentTime; // Timer zurücksetzen
            }
        }

        // Platzieren (Rechtsklick) - Funktioniert nur nach Ablauf des Cooldowns
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            if (currentTime - lastPlaceTime >= PLACE_COOLDOWN) {
                level.setVoxelAtWorldPos(lastRaycastResult.adjacentPos, (byte) 1); // Platziere Stein
                lastPlaceTime = currentTime; // Timer zurücksetzen
            }
        }
    }

    /**
     * Berechnet die Bewegung der Kamera inklusive Schwerkraft, Voxel-Bodenhaftung und Sprung-Logik.
     */
    public void updateCameraMovement(Camera camera, Level level, float deltaTime) {
        // --- 1. Kamera-Rotation (Bleibt identisch) ---
        camera.setYaw(camera.getYaw() + mouseXDelta * mouseSensitivity);
        camera.setPitch(camera.getPitch() - mouseYDelta * mouseSensitivity);

        if (camera.getPitch() > 89.0f) camera.setPitch(89.0f);
        if (camera.getPitch() < -89.0f) camera.setPitch(-89.0f);
        camera.updateVectors();

        Vector3f position = camera.getPosition();

        // --- 2. Schwerkraft-Berechnung ---
        if (collisionEnabled) {
            // Wenn wir fallen, erhöht sich die Fallgeschwindigkeit kontinuierlich
            verticalVelocity += gravity * deltaTime;

            // Terminal-Velocity einbauen (Sicherheitsgrenze, um Tunneln durch Blöcke zu verhindern)
            if (verticalVelocity < -50.0f) {
                verticalVelocity = -50.0f;
            }
        } else {
            // Im No-Clip-Modus gibt es keine Schwerkraft
            verticalVelocity = 0.0f;
        }

        // --- 3. Tastatur-Eingaben für die Horizontale (Gehen) ---
        velocity.set(0, 0, 0);

        // Wir holen uns die Blickrichtung, projizieren sie aber rein auf die X/Z Ebene (Boden).
        // Dadurch läuft der Spieler nicht langsamer oder fliegt hoch, wenn er in den Himmel schaut!
        Vector3f forwardOnGround = new Vector3f(camera.getDirection().x, 0.0f, camera.getDirection().z);
        if (forwardOnGround.lengthSquared() > 0) {
            forwardOnGround.normalize();
        }

        if (isKeyDown(GLFW_KEY_W)) velocity.add(forwardOnGround);
        if (isKeyDown(GLFW_KEY_S)) velocity.sub(forwardOnGround);
        if (isKeyDown(GLFW_KEY_A)) velocity.sub(camera.getRight());
        if (isKeyDown(GLFW_KEY_D)) velocity.add(camera.getRight());



        // --- 4. Sprung- und Sink-Eingabe basierend auf dem Spielmodus ---
        boolean isStrgDown = isKeyDown(GLFW_KEY_LEFT_CONTROL);
        float speedThisFrame = movementSpeed;

        if (collisionEnabled) {
            // SPRINTEN IM SURVIVAL (Geschwindigkeit erhöhen)
            if (isStrgDown) {
                speedThisFrame *= 2.5f;
            }

            // SPRINGEN IM SURVIVAL (Nur wenn man auf dem Boden steht)
            if (isKeyDown(GLFW_KEY_SPACE) && isGrounded) {
                verticalVelocity = jumpForce;
                isGrounded = false;
            }
        } else {
            speedThisFrame *= 2;
            // STEUERUNG IM CREATIVE / NO-CLIP
            // Auch im Fliegen erhöht Shift die Fluggeschwindigkeit (Schnellflug)
            if (isStrgDown) {
                speedThisFrame *= 3.0f;
            }

            // Leertaste fliegt nach oben
            if (isKeyDown(GLFW_KEY_SPACE)) {
                position.y += speedThisFrame * deltaTime;
            }
            // KORREKTUR: Shift-Taste fliegt im Creative-Modus nach unten!
            if (isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
                position.y -= speedThisFrame * deltaTime;
            }
        }

        // Horizontale Bewegung normalisieren und skalieren
        if (velocity.lengthSquared() > 0) {
            velocity.normalize().mul(speedThisFrame * deltaTime);
        }

        // --- 5. Physikalische Achsen-Kollisionsabwicklung (Sliding) ---
        if (collisionEnabled) {
            // A) Horizontale Bewegung anwenden (X und Z)
            position.x += velocity.x;
            if (level.checkCollision(position.x - playerRadius, position.y - playerHeight, position.z - playerRadius,
                    position.x + playerRadius, position.y,                position.z + playerRadius)) {
                position.x -= velocity.x;
            }

            position.z += velocity.z;
            if (level.checkCollision(position.x - playerRadius, position.y - playerHeight, position.z - playerRadius,
                    position.x + playerRadius, position.y,                position.z + playerRadius)) {
                position.z -= velocity.z;
            }

            // B) Vertikale Bewegung anwenden (Y-Achse / Fallen)
            float originalY = position.y;
            position.y += verticalVelocity * deltaTime;

            if (level.checkCollision(position.x - playerRadius, position.y - playerHeight, position.z - playerRadius,
                    position.x + playerRadius, position.y,                position.z + playerRadius)) {

                // Kollision erkannt!
                position.y = originalY; // Bewegung zurücksetzen

                if (verticalVelocity < 0.0f) {
                    // Wir sind nach unten gefallen und aufgeschlagen -> Wir stehen auf dem Boden!
                    isGrounded = true;
                }

                verticalVelocity = 0.0f; // Sturz-/Steiggeschwindigkeit stoppen
            } else {
                // Wenn wir uns frei nach unten bewegen, ohne auf Granit zu stoßen, fliegen wir in der Luft
                if (verticalVelocity != 0.0f) {
                    isGrounded = false;
                }
            }
        } else {
            // No-Clip-Modus: Einfach die weiche X/Z Bewegung addieren
            position.add(velocity);
        }

        camera.updateVectors();
    }

    public void handleModeSwitch() {
        boolean vIsDown = isKeyDown(GLFW_KEY_V);

        // Registriert den Tastendruck genau in dem Moment, in dem die Taste gedrückt wird (Edge-Trigger)
        if (vIsDown && !vKeyWasPressed) {
            collisionEnabled = !collisionEnabled; // Kollision und Schwerkraft umkehren
            verticalVelocity = 0.0f;              // Fallgeschwindigkeit beim Wechseln zurücksetzen
            isGrounded = false;

            if (collisionEnabled) {
                System.out.println("[Spielmodus] Survival-Modus: Schwerkraft und Kollision AKTIV.");
            } else {
                System.out.println("[Spielmodus] Creative-Modus: Freies Fliegen und No-Clip AKTIV.");
            }
        }

        vKeyWasPressed = vIsDown; // Zustand für den nächsten Frame merken
    }

    public boolean isKeyDown(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    public float getMouseXDelta() { return mouseXDelta; }
    public float getMouseYDelta() { return mouseYDelta; }
    public boolean isCollisionEnabled() { return collisionEnabled; }

    // Nützlich, um mit einer Taste (z.B. V) den Flugmodus wieder einzuschalten
    public void setCollisionEnabled(boolean enabled) { this.collisionEnabled = enabled; }

    public RaycastResult getLastRaycastResult() {
        return lastRaycastResult;
    }
}
