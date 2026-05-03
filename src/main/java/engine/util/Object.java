package engine.util;

import org.joml.Matrix4f;

public class Object {
    Mesh mesh;
    Matrix4f modelMatrix;

    public Object(Mesh mesh) {
        this.mesh = mesh;
    }

    public Matrix4f getModelMatrix() {
        return modelMatrix;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void cleanup() {
        mesh.cleanup();
    }
}
