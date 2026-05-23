#version 330 core

uniform mat4 view;
uniform mat4 projection;
uniform ivec3 chunkDimensions;
uniform vec3 chunkWorldPos;
uniform usampler3D voxelTex3D;

flat out uint BlockID;
out vec3 FragPos;
out vec2 TexCoords;
out vec3 Normal;

// Lokale Eckpunkte eines Würfels (0.0 bis 1.0)
const vec3 vertices[] = vec3[](
    vec3(0,0,0), vec3(1,0,0), vec3(1,1,0), vec3(0,1,0),
    vec3(0,0,1), vec3(1,0,1), vec3(1,1,1), vec3(0,1,1)
);

// Absolut fehlerfreie Index-Wicklung für CCW-Außenseiten
const int faceIndices[] = int[](
    1,0,3, 1,3,2, // Hinten (Z-)
    4,5,6, 4,6,7, // Vorne (Z+)
    0,4,7, 0,7,3, // Links (X-)
    5,1,2, 5,2,6, // Rechts (X+)
    3,6,2, 3,7,6, // Oben (Y+)
    0,1,5, 0,5,4  // Unten (Y-)
);

const vec3 faceNormals[] = vec3[](
    vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0), vec3(0,1,0), vec3(0,-1,0)
);

const vec2 uvCoords[] = vec2[](
    vec2(1,0), vec2(0,0), vec2(0,1), vec2(1,0), vec2(0,1), vec2(1,1)
);

void main() {
    // Präzise Bit- und Integer-Berechnung, um Treiber-Rundungsfehler zu eliminieren
    int vertexIdx = gl_VertexID % 6;
    int faceIdx   = (gl_VertexID / 6) % 6;
    int voxelIdx  = gl_VertexID / 36;

    // Lokale 3D-Gitterkoordinaten im Chunk extrahieren
    int x = voxelIdx % chunkDimensions.x;
    int y = (voxelIdx / chunkDimensions.x) % chunkDimensions.y;
    int z = voxelIdx / (chunkDimensions.x * chunkDimensions.y);
    ivec3 localPos = ivec3(x, y, z);

    // Falls die ID außerhalb des Chunks liegt, Vertex verwerfen
    if (z >= chunkDimensions.z || y >= chunkDimensions.y || x >= chunkDimensions.x) {
        gl_Position = vec4(0.0);
        return;
    }

    // 3D-Textur auslesen
    uint blockType = texelFetch(voxelTex3D, localPos, 0).r;

    // Wenn Luft (0), wirf den Block weg
    if (blockType == 0u) {
        gl_Position = vec4(0.0);
        return;
    }

    // --- GPU-Basiertes Face Culling ---
    ivec3 neighborPos = localPos + ivec3(faceNormals[faceIdx]);

    // Nachbar nur prüfen, wenn er innerhalb des Chunks liegt
    if (neighborPos.x >= 0 && neighborPos.x < chunkDimensions.x &&
    neighborPos.y >= 0 && neighborPos.y < chunkDimensions.y &&
    neighborPos.z >= 0 && neighborPos.z < chunkDimensions.z) {

        uint neighborType = texelFetch(voxelTex3D, neighborPos, 0).r;
        if (neighborType > 0u) {
            gl_Position = vec4(0.0); // Verdeckte Innenseite -> unsichtbar machen
            return;
        }
    }

    // Geometrie final berechnen
    int lookupIdx = faceIdx * 6 + vertexIdx;
    vec3 localVertexPos = vertices[faceIndices[lookupIdx]];
    vec3 worldVertexPos = chunkWorldPos + vec3(localPos) + localVertexPos;

    FragPos = worldVertexPos;
    Normal = faceNormals[faceIdx];
    TexCoords = uvCoords[vertexIdx];
    BlockID = blockType;

    gl_Position = projection * view * vec4(worldVertexPos, 1.0);
}