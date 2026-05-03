package engine.rendering;

import engine.lighting.PointLight;
import engine.util.Object;

import java.util.List;

public class Scene {
    List<PointLight> lights;
    List<Object> objects;

    public Scene(List<PointLight> lights, List<Object> objects) {
        this.lights = lights;
        this.objects = objects;
    }
}
