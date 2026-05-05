package engine.util;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Matrix4f projection = new Matrix4f();
    private Matrix4f view = new Matrix4f();

    private Vector3f position = new Vector3f(0, 0, 5);
    private float yaw = -90f, pitch = 0f;
    private float baseSpeed = 5.0f, sensitivity = 13.0f;

    public void update(float aspectRatio) {
        projection.setPerspective((float) Math.toRadians(45.0f), aspectRatio, Constants.ZNear, Constants.ZFar);
    }

    public void handleInput(InputManager input, float deltaTime) {

        float currentSpeed = baseSpeed * deltaTime;

        float currentMouseSpeed = sensitivity * deltaTime;

        yaw += input.getMouseDeltaX() * currentMouseSpeed;
        pitch -= input.getMouseDeltaY() * currentMouseSpeed;
        pitch = Math.max(-89.9f, Math.min(89.9f, pitch));

        // 2. Richtungsvektoren berechnen
        Vector3f front = new Vector3f(
                (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))),
                (float) Math.sin(Math.toRadians(pitch)),
                (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)))
        ).normalize();

        Vector3f horizontalFront = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f right = new Vector3f(horizontalFront).cross(0, 1, 0).normalize();

        if (input.isActionActive("FORWARD"))  position.add(new Vector3f(horizontalFront).mul(currentSpeed));
        if (input.isActionActive("BACKWARD")) position.sub(new Vector3f(horizontalFront).mul(currentSpeed));
        if (input.isActionActive("LEFT"))     position.sub(new Vector3f(right).mul(currentSpeed));
        if (input.isActionActive("RIGHT"))    position.add(new Vector3f(right).mul(currentSpeed));

        if (input.isActionActive("UP"))       position.y += currentSpeed;
        if (input.isActionActive("DOWN"))     position.y -= currentSpeed;

        view.setLookAt(position, new Vector3f(position).add(front), new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjection() { return projection; }
    public Matrix4f getView() { return view; }
    public void setView(Matrix4f view) { this.view = view; }

    public void setPos(Vector3f pos) {
        this.position.set(pos);
        view.setTranslation(pos);
    }

    public Vector3f getPosition() {
        return position;
    }
}