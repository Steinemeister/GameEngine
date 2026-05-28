#version 330 core

uniform mat4 ortho;     // Orthografische Matrix (Bildschirmgröße)
uniform vec2 position;  // Pixel-Position auf dem Bildschirm
uniform vec2 size;      // Pixel-Größe des Elements

out vec2 TexCoords;

// Generiert die 4 Ecken eines Dreiecksstreifens (Triangle Strip) von (0,0) bis (1,1)
const vec2 positions[4] = vec2[](
vec2(0.0, 1.0), // Oben Links
vec2(0.0, 0.0), // Unten Links
vec2(1.0, 1.0), // Oben Rechts
vec2(1.0, 0.0)  // Unten Rechts
);

void main() {
    vec2 pos = positions[gl_VertexID];
    TexCoords = pos; // Da das Quad von (0,0) bis (1,1) geht, sind die UVs identisch!

    // Skaliere das Quad auf die Pixelgröße und verschiebe es an die Bildschirmposition
    vec2 finalPixelPos = position + (pos * size);
    gl_Position = ortho * vec4(finalPixelPos, 0.0, 1.0);
}