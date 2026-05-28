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
1,0,3, 1,3,2, // Hinten
4,5,6, 4,6,7, // Vorne
0,4,7, 0,7,3, // Links
5,1,2, 5,2,6, // Rechts
3,6,2, 3,7,6, // Oben
0,1,5, 0,5,4  // Unten
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

    ivec3 texPos = localPos + ivec3(1);
    uint blockType = texelFetch(voxelTex3D, texPos, 0).r;

    if (blockType != 4u) {
        gl_Position = vec4(0.0);
        return;
    }

    // --- NACHBARSCHAFTS-CULLING ---
    ivec3 neighborTexPos = texPos + ivec3(faceNormals[faceIdx]);
    if (neighborTexPos.x >= 0 && neighborTexPos.x < (chunkDimensions.x + 2) &&
    neighborTexPos.y >= 0 && neighborTexPos.y < (chunkDimensions.y + 2) &&
    neighborTexPos.z >= 0 && neighborTexPos.z < (chunkDimensions.z + 2)) {

        uint neighborType = texelFetch(voxelTex3D, neighborTexPos, 0).r;
        if (neighborType != 0u) {
            gl_Position = vec4(0.0);
            return;
        }
    }

    int lookupIdx = faceIdx * 6 + vertexIdx;
    vec3 localVertexPos = vertices[faceIndices[lookupIdx]];
    vec3 worldVertexPos = chunkWorldPos + vec3(localPos) + localVertexPos;

    // Minimales Anheben gegen Z-Fighting an den Rändern
    worldVertexPos += faceNormals[faceIdx] * 0.002;

    FragPos = worldVertexPos;
    Normal = faceNormals[faceIdx];
    TexCoords = uvCoords[vertexIdx];
    BlockID = blockType;
    ViewSpacePos = vec3(view * vec4(worldVertexPos, 1.0));

    gl_Position = projection * view * vec4(worldVertexPos, 1.0);
}