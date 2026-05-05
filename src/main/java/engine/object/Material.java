package engine.object;

public record Material (Texture diffuseTexture,
        Texture ambientTexture,
        Texture specularTexture,
        Texture specularExponentTexture) {

}
