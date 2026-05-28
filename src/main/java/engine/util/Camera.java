package engine.util;

import engine.world.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private float yaw;
    private float pitch;

    private final Vector3f direction;
    private final Vector3f up;
    private final Vector3f right;

    private final Matrix4f view;
    private final Matrix4f projection;

    private final float fov = 70.0f;
    private final float zFar = Constants.ZFar;

    public Camera() {
        this.position = new Vector3f(8.0f, 20.0f, 25.0f);
        this.yaw = -90.0f;
        this.pitch = -30.0f;

        this.direction = new Vector3f();
        this.up = new Vector3f(0.0f, 1.0f, 0.0f);
        this.right = new Vector3f();

        this.view = new Matrix4f();
        this.projection = new Matrix4f();

        updateVectors();
    }

    public void update(float aspectRatio) {
        Vector3f target = new Vector3f(position).add(direction);
        view.identity().lookAt(position, target, up);
        projection.identity().perspective((float) Math.toRadians(fov), aspectRatio, 0.1f, zFar);
    }

    public void updateVectors() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        direction.x = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        direction.y = (float) Math.sin(pitchRad);
        direction.z = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        direction.normalize();

        direction.cross(0.0f, 1.0f, 0.0f, right).normalize();
        right.cross(direction, up).normalize();
    }

    // --- Reine Getter und Setter ---
    public Vector3f getPosition() { return position; }
    public Vector3f getDirection() { return direction; }
    public Vector3f getRight() { return right; }
    public Matrix4f getView() { return view; }
    public Matrix4f getProjection() { return projection; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; updateVectors(); }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; updateVectors(); }

    public void setPos(Vector3f pos) {
        this.position = pos;
    }

    public Vector3f getFront() {
        return this.direction;
    }
}