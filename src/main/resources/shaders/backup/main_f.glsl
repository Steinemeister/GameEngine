#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
in vec3 ViewSpacePos;
flat in uint BlockID;

uniform sampler2D textureAtlas;
uniform float far_plane;

// NEU: Richtungslicht-Uniforms statt PointLight-Struktur
uniform vec3 sunDirection;
uniform vec3 sunColor;
uniform int renderPass;

void main() {
    if (renderPass == 0 && BlockID == 4u) discard;
    if (renderPass == 1 && BlockID != 4u) discard;

    float atlasSize = 4.0;
    int atlasIndex = int(BlockID) - 1;
    if (atlasIndex < 0) atlasIndex = 0;

    float uOffset = float(atlasIndex % 4) / atlasSize;
    float vOffset = float(atlasIndex / 4) / atlasSize;

    vec2 localTexCoords = clamp(TexCoords, 0.001, 0.999);
    vec2 atlasTexCoords = (localTexCoords / atlasSize) + vec2(uOffset, vOffset);
    vec3 texColor = texture(textureAtlas, atlasTexCoords).rgb;

    float alpha = 1.0;

    if (BlockID == 4u) {
        texColor = mix(texColor, vec3(0.0, 0.3, 0.7), 0.5);
        alpha = 0.5;
    }

    // --- NEUE RICHTUNGSLICHT BERECHNUNG ---
    vec3 normal = normalize(Normal);

    // Ambienter Lichtanteil (Grundhelligkeit im Schatten)
    vec3 ambient = 0.30 * texColor;

    // Diffuser Lichtanteil (Das Licht wird invertiert, da sunDirection zum Boden zeigt)
    vec3 lightDir = normalize(-sunDirection);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * sunColor;

    // Finale Voxel-Farbe zusammensetzen
    vec3 finalVoxelColor = (ambient + diffuse) * texColor;

    // 3D Nebel Berechnung
    float distance = length(ViewSpacePos);
    float fogStart = far_plane * 0.4;
    float fogEnd = far_plane * 0.95;
    float fogFactor = clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);

    // Die Nebelfarbe passt sich leicht an die Sonnenfarbe an (z.B. rötlich beim Sonnenuntergang)
    vec3 fogColor = vec3(0.1, 0.1, 0.1) * sunColor;

    vec3 finalColorWithFog = mix(finalVoxelColor, fogColor, fogFactor);

    FragColor = vec4(finalColorWithFog, alpha);
}