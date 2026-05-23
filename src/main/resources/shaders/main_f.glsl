#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
in vec3 ViewSpacePos; // Empfängt die Kamera-Distanz-Daten
flat in uint BlockID;

uniform sampler2D textureAtlas;
uniform vec3 lightPos;
uniform float far_plane;

void main() {
    float atlasSize = 4.0;
    int atlasIndex = int(BlockID) - 1;
    if (atlasIndex < 0) atlasIndex = 0;

    float uOffset = float(atlasIndex % 4) / atlasSize;
    float vOffset = float(atlasIndex / 4) / atlasSize;

    vec2 localTexCoords = clamp(TexCoords, 0.001, 0.999);
    vec2 atlasTexCoords = (localTexCoords / atlasSize) + vec2(uOffset, vOffset);
    vec3 texColor = texture(textureAtlas, atlasTexCoords).rgb;

    // 1. Beleuchtungsberechnung
    vec3 normal = normalize(Normal);
    vec3 ambient = 0.25 * texColor;
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * vec3(1.0);
    vec3 finalVoxelColor = ambient + diffuse * texColor;

    // 2. --- 3D NEBEL BERECHNUNG ---
    // Distanz vom Fragment zur Kamera berechnen
    float distance = length(ViewSpacePos);

    // Nebel-Start (z.B. ab 40% der Sichtweite) und Nebel-Ende definieren
    float fogStart = far_plane * 0.4;
    float fogEnd = far_plane * 0.95; // Etwas vor dem harten Abschneiden enden lassen

    // Nebelfaktor berechnen (0.0 = klar, 1.0 = dicker Nebel)
    float fogFactor = clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);

    // Die Hintergrundfarbe deines RenderManagers (glClearColor war: 0.1, 0.1, 0.1)
    vec3 fogColor = vec3(0.1, 0.1, 0.1);

    // Voxel-Farbe basierend auf der Distanz mit der Hintergrundfarbe mischen
    vec3 finalColorWithFog = mix(finalVoxelColor, fogColor, fogFactor);

    FragColor = vec4(finalColorWithFog, 1.0);
}