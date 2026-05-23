#version 330 core
layout (triangles) in;
layout (triangle_strip, max_vertices=18) out;

uniform mat4 shadowMatrices[6]; // Fest auf 6 Matrizen gesetzt!

in vec3 VashWorldPos[]; // Kommt aus dem Vertex-Shader als Array von 3 Eckpunkten
out vec3 WorldPosOut;   // Geht zum Fragment-Shader

void main() {
    for(int face = 0; face < 6; ++face) {
        gl_Layer = face; // Bestimmt, in welche Cube-Map-Seite gerendert wird

        for(int i = 0; i < 3; ++i) {
            WorldPosOut = VashWorldPos[i]; // Übergabe der reinen Weltkoordinate für die Distanz

            // Jetzt wird die Matrix korrekt auf die rohe Weltposition angewendet!
            gl_Position = shadowMatrices[face] * vec4(WorldPosOut, 1.0);
            EmitVertex();
        }
        EndPrimitive();
    }
}