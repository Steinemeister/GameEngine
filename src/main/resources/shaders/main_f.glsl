#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
flat in uint BlockID;

uniform sampler2D textureAtlas;
uniform vec3 lightPos;

void main() {
    float atlasSize = 4.0;

    int atlasIndex = int(BlockID) - 1;
    if (atlasIndex < 0) atlasIndex = 0;

    float uOffset = float(atlasIndex % 4) / atlasSize;
    float vOffset = float(atlasIndex / 4) / atlasSize;

    // clamp verhindert Texturbluten an den Kanten bei 1x1 Pixeln
    vec2 localTexCoords = clamp(TexCoords, 0.001, 0.999);
    vec2 atlasTexCoords = (localTexCoords / atlasSize) + vec2(uOffset, vOffset);
    vec3 texColor = texture(textureAtlas, atlasTexCoords).rgb;

    // Einfache, fehlerfreie Phong/Diffuse-Lichtberechnung
    vec3 normal = normalize(Normal);
    vec3 ambient = 0.25 * texColor; // Grundhelligkeit der Blöcke

    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * vec3(1.0, 1.0, 1.0); // Weißes Licht

    FragColor = vec4(ambient + diffuse * texColor, 1.0);
}