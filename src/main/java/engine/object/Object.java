package engine.object;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Object {
    private final Mesh mesh;
    private final Texture texture;

    private Vector3f position;
    private Vector3f rotation;
    private float scale;

    public Object(Mesh mesh, Texture texture) {
        this.mesh = mesh;
        this.texture = texture;

        this.position = new Vector3f(0, 0, 0);
        this.rotation = new Vector3f(0, 0, 0);
        this.scale = 1.0f;
    }

    public Matrix4f getModelMatrix() {
        return new Matrix4f()
                .translate(position)
                .rotateXYZ((float)Math.toRadians(rotation.x),
                        (float)Math.toRadians(rotation.y),
                        (float)Math.toRadians(rotation.z))
                .scale(scale);
    }

    // Getter und Setter für die Transformationen
    public Mesh getMesh() { return mesh; }
    public Vector3f getPosition() { return position; }
    public Vector3f getRotation() { return rotation; }
    public void setScale(float scale) { this.scale = scale; }
    public Texture getTexture() { return texture; }
}
