#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
in vec3 ViewSpacePos;
flat in uint BlockID;
in float WaveHeight; // Empfängt die Wellenhöhe

uniform sampler2D textureAtlas;
uniform float far_plane;
uniform float time; // Neu von Java

uniform vec3 sunDirection;
uniform vec3 sunColor;

void main() {
    float atlasSize = 4.0;
    int atlasIndex = 3; // Wasser hat ID 4u -> Index 3 im Atlas

    float uOffset = float(atlasIndex % 4) / atlasSize;
    float vOffset = float(atlasIndex / 4) / atlasSize;

    // --- NEU: ANIMIERTE WASSER-TEXTUR-KOORDINATEN ---
    // Zwei gegeneinander verschobene UV-Layer simulieren Strömung
    vec2 uvScroll1 = TexCoords + vec2(time * 0.015, time * 0.01);
    vec2 uvScroll2 = TexCoords + vec2(time * -0.01, time * 0.015);

    vec2 localTexCoords1 = clamp(uvScroll1, 0.001, 0.999);
    vec2 localTexCoords2 = clamp(uvScroll2, 0.001, 0.999);

    vec2 atlasTexCoords1 = (localTexCoords1 / atlasSize) + vec2(uOffset, vOffset);
    vec2 atlasTexCoords2 = (localTexCoords2 / atlasSize) + vec2(uOffset, vOffset);

    // Beide Textur-Bilder mischen
    vec3 texColor1 = texture(textureAtlas, atlasTexCoords1).rgb;
    vec3 texColor2 = texture(textureAtlas, atlasTexCoords2).rgb;
    vec3 texColor = mix(texColor1, texColor2, 0.5);

    // Physikalische Blau-Einfärbung des Tiefenwassers
    texColor = mix(texColor, vec3(0.0, 0.25, 0.65), 0.5);

    // --- NEU: SCHAUMKRONEN AUF DEN WELLENKUPPEN ---
    if (Normal.y > 0.5 && WaveHeight > 0.04) {
        // Je höher die Welle schlägt, desto weißer wird die Gischt
        float foamFactor = smoothstep(0.04, 0.12, WaveHeight);
        texColor = mix(texColor, vec3(0.9, 0.95, 1.0), foamFactor * 0.6);
    }

    // --- BELEUCHTUNG & GLANZPUNKTE (Specular) ---
    vec3 normal = normalize(Normal);
    vec3 lightDir = normalize(-sunDirection);

    // Ambient und Diffus
    vec3 ambient = 0.35 * texColor;
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * sunColor;

    // NEU: Spiegelungseffekt (Specular) direkt auf der Wasseroberfläche
    vec3 viewDir = normalize(-ViewSpacePos);
    vec3 halfDir = normalize(lightDir + viewDir);
    float specFactor = pow(max(dot(normal, halfDir), 0.0), 32.0); // 32.0 bestimmt die Schärfe des Glanzes
    vec3 specular = specFactor * sunColor * 0.8;

    // Nur tagsüber spiegeln, nachts glänzt das Wasser kaum
    float sunHeight = -sunDirection.y;
    if (sunHeight < 0.0) specular = vec3(0.0);

    // Finale Farbe vor Nebel zusammensetzen
    vec3 finalVoxelColor = (ambient + diffuse) * texColor + specular;

    // 3D Nebel Berechnung
    float distance = length(ViewSpacePos);
    float fogStart = far_plane * 0.4;
    float fogEnd = far_plane * 0.95;
    float fogFactor = clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);

    // Nebel/Horizontfarbe (Sollte mit deiner Skybox-Horizontfarbe übereinstimmen)
    vec3 horizonColor = vec3(0.7, 0.8, 0.9) * sunColor;
    vec3 finalColorWithFog = mix(finalVoxelColor, horizonColor, fogFactor);

    // Alpha-Wert: Bei steilem Blickwinkel transparenter, am Horizont deckender (Fresnel-Effekt angedeutet)
    float cosView = max(dot(normal, viewDir), 0.0);
    float alpha = mix(0.75, 0.4, cosView);

    FragColor = vec4(finalColorWithFog, alpha);
}