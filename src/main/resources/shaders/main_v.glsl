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
out vec3 ViewSpacePos;

const vec3 vertices[] = vec3[](
vec3(0,0,0), vec3(1,0,0), vec3(1,1,0), vec3(0,1,0),
vec3(0,0,1), vec3(1,0,1), vec3(1,1,1), vec3(0,1,1)
);

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

    // --- KORRIGIERTES GPU FACE CULLING ---
    ivec3 neighborPos = localPos + ivec3(faceNormals[faceIdx]);

    // Wir prüfen, ob der Nachbarblock sich im SELBEN Chunk befindet
    if (neighborPos.x >= 0 && neighborPos.x < chunkDimensions.x &&
    neighborPos.y >= 0 && neighborPos.y < chunkDimensions.y &&
    neighborPos.z >= 0 && neighborPos.z < chunkDimensions.z) {

        uint neighborType = texelFetch(voxelTex3D, neighborPos, 0).r;
        if (neighborType > 0u) {
            gl_Position = vec4(0.0); // Innerer Block ist verdeckt -> Ausblenden
            return;
        }
    } else {
        // KORREKTUR: Der Nachbarblock liegt in einem ANDEREN Chunk!
        // Da wir im Shader keinen Zugriff auf die Nachbar-Textur haben, erzwingen wir hier,
        // dass die Außenwand gezeichnet wird. Dadurch schaust du nahtlos in den nächsten Chunk!
    }

    int lookupIdx = faceIdx * 6 + vertexIdx;
    vec3 localVertexPos = vertices[faceIndices[lookupIdx]];
    vec3 worldVertexPos = chunkWorldPos + vec3(localPos) + localVertexPos;

    FragPos = worldVertexPos;
    Normal = faceNormals[faceIdx];
    TexCoords = uvCoords[vertexIdx];
    BlockID = blockType;
    ViewSpacePos = vec3(view * vec4(worldVertexPos, 1.0));

    gl_Position = projection * view * vec4(worldVertexPos, 1.0);
}