#version 330 core
layout (triangles) in;
layout (triangle_strip, max_vertices=18) out;

uniform mat4 shadowMatrices[6];

out vec4 FragPos; // FragPos wird an Fragment Shader weitergegeben

void main() {
    for(int face = 0; face < 6; ++face) {
        gl_Layer = face; // Eingebautes GLSL-Attribut: Bestimmt die Cubemap-Seite
        for(int i = 0; i < 3; ++i) {
            FragPos = gl_in[i].gl_Position;
            gl_Position = shadowMatrices[face] * FragPos;
            EmitVertex();
        }
        EndPrimitive();
    }
}