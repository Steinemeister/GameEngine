#version 330 core

layout (location = 0) in int aVertexData;

uniform mat4 view;
uniform mat4 projection;
uniform mat4 model;
uniform vec3 chunkWorldPos; // REAKTIVIERT: Die absolute Weltposition dieses Chunks

flat out int BlockID;
out vec3 FragPos;
out vec2 TexCoords;
out vec3 Normal;
out vec3 ViewSpacePos;

const vec3 faceNormals[] = vec3[](
vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0), vec3(0,1,0), vec3(0,-1,0)
);

const vec2 uvCoords[] = vec2[](
vec2(0.0, 0.0),
vec2(1.0, 0.0),
vec2(0.0, 1.0),
vec2(1.0, 1.0)
);

void main() {
    // --- BIT-UNPACKING ---
    int localX = (aVertexData >> 0)  & 0x3F;
    int localY = (aVertexData >> 6)  & 0x3F;
    int localZ = (aVertexData >> 12) & 0x3F;

    vec3 worldVertexPos = chunkWorldPos + vec3(float(localX), float(localY), float(localZ));

    int normalIdx = (aVertexData >> 18) & 0x07;

    // KORREKTUR: Entpacke die U- und V-Koordinaten bitweise
    int packedUV  = (aVertexData >> 21) & 0x03;
    float uCoord  = float(packedUV & 1);
    float vCoord  = float((packedUV >> 1) & 1);

    int blockType = (aVertexData >> 23) & 0xFF;

    // --- DATEN-WEITERGABE ---
    vec4 finalWorldPos = model * vec4(worldVertexPos, 1.0);

    FragPos = finalWorldPos.xyz;
    Normal = faceNormals[normalIdx];
    TexCoords = vec2(uCoord, vCoord); // Perfekt zugewiesene UV-Koordinaten!
    BlockID = blockType;
    ViewSpacePos = vec3(view * finalWorldPos);

    gl_Position = projection * view * finalWorldPos;
}