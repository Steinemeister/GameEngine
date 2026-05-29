#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
in vec3 ViewSpacePos;
flat in int BlockID;

uniform sampler2D textureAtlas;
uniform float far_plane;

uniform vec3 sunDirection;
uniform vec3 sunColor;

void main() {
    float atlasSize = 4.0;
    int atlasIndex = int(BlockID) - 1;
    if (atlasIndex < 0) atlasIndex = 0;

    float uOffset = float(atlasIndex % 4) / atlasSize;
    float vOffset = float(atlasIndex / 4) / atlasSize;

    vec2 localTexCoords = clamp(TexCoords, 0.001, 0.999);
    vec2 atlasTexCoords = (localTexCoords / atlasSize) + vec2(uOffset, vOffset);
    vec3 texColor = texture(textureAtlas, atlasTexCoords).rgb;

    // --- RICHTUNGSLICHT BERECHNUNG ---
    vec3 normal = normalize(Normal);

    // Ambienter Lichtanteil
    vec3 ambient = 0.50 * texColor;

    // Diffuser Lichtanteil
    vec3 lightDir = normalize(-sunDirection);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * sunColor;

    // Finale Voxel-Farbe zusammensetzen
    vec3 finalVoxelColor = (ambient + diffuse) * texColor;

    FragColor = vec4(finalVoxelColor, 1.0);
}