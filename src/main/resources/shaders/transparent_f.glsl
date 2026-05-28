#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
in vec3 ViewSpacePos;
flat in uint BlockID;

uniform sampler2D textureAtlas;
uniform float far_plane;

uniform vec3 sunDirection;
uniform vec3 sunColor;

void main() {
    // Statische, klare Voxel-Wasserfarbe
    vec3 texColor = vec3(0.05, 0.25, 0.60);

    vec3 normal = normalize(Normal);

    // --- BELEUCHTUNG (Klassisch flach) ---
    vec3 lightDir = normalize(-sunDirection);

    // Ambienter Lichtanteil
    vec3 ambient = 0.40 * texColor;

    // Diffuser Lichtanteil
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * sunColor;

    // Finale Voxel-Farbe vor dem Nebel
    vec3 finalVoxelColor = (ambient + diffuse) * texColor;

    // 3D Nebel Berechnung (für weiche Übergänge am Horizont)
    float distance = length(ViewSpacePos);
    float fogStart = far_plane * 0.4;
    float fogEnd = far_plane * 0.95;
    float fogFactor = clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);

    vec3 horizonColor = vec3(0.7, 0.8, 0.9) * sunColor;
    vec3 finalColorWithFog = mix(finalVoxelColor, horizonColor, fogFactor);

    // Alpha-Berechnung basierend auf dem Blickwinkel (Fresnel-Annäherung)
    vec3 viewDir = normalize(-ViewSpacePos);
    float cosView = max(dot(normal, viewDir), 0.0);
    float alpha = mix(0.8, 0.4, cosView);

    FragColor = vec4(finalColorWithFog, alpha);
}