#version 330 core
layout (location = 0) in vec3 aPos;

uniform mat4 view;
uniform mat4 projection;
uniform vec3 blockPos; // Die globale Position des anvisierten Blocks

void main() {
    // Verschiebe die Ecken des Linien-Würfels an die exakte Block-Position
    gl_Position = projection * view * vec4(aPos + blockPos, 1.0);
}