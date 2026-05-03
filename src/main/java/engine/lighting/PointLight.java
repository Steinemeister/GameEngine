package engine.lighting;

import org.joml.Vector3f;

public class PointLight {
    public Vector3f position;
    public Vector3f color;
    public float intensity;
    public CubeMapFBO fbo;

    public PointLight(Vector3f pos, Vector3f col, float intensity) {
        this.position = pos;
        this.color = col;
        this.intensity = intensity;
        fbo = new CubeMapFBO(this);
    }
}
