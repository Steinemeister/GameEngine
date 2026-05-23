#version 330 core

uniform ivec3 chunkDimensions;
uniform vec3 chunkWorldPos;
uniform usampler3D voxelTex3D;

out vec3 VashWorldPos; // Schickt die reine Weltposition zum Geometrie-Shader

const vec3 vertices[] = vec3[](
vec3(0,0,0), vec3(1,0,0), vec3(1,1,0), vec3(0,1,0),
vec3(0,0,1), vec3(1,0,1), vec3(1,1,1), vec3(0,1,1)
);

const int faceIndices[] = int[](
1,0,3, 1,3,2, // Hinten
4,5,6, 4,6,7, // Vorne
0,4,7, 0,7,3, // Links
5,1,2, 5,2,6, // Rechts
3,6,2, 3,7,6, // Oben
0,1,5, 0,5,4  // Unten
);

void main() {
    int vertexIdx = gl_VertexID % 6;
    int faceIdx   = (gl_VertexID / 6) % 6;
    int voxelIdx  = gl_VertexID / 36;

    int x = voxelIdx % chunkDimensions.x;
    int y = (voxelIdx / chunkDimensions.x) % chunkDimensions.y;
    int z = voxelIdx / (chunkDimensions.x * chunkDimensions.y);
    ivec3 localPos = ivec3(x, y, z);

    if (z >= chunkDimensions.z || y >= chunkDimensions.y || x >= chunkDimensions.x) {
        gl_Position = vec4(0.0);
        return;
    }

    uint blockType = texelFetch(voxelTex3D, localPos, 0).r;
    if (blockType == 0u) {
        gl_Position = vec4(0.0);
        return;
    }

    int lookupIdx = faceIdx * 6 + vertexIdx;
    vec3 localVertexPos = vertices[faceIndices[lookupIdx]];

    // Die echte Weltposition berechnen
    VashWorldPos = chunkWorldPos + vec3(localPos) + localVertexPos;

    // Wichtig für den Geometrie-Shader: Wir legen die Weltposition in gl_Position!
    gl_Position = vec4(VashWorldPos, 1.0);
}