#version 330 core
in vec4 FragPos;
uniform vec3 lightPos;
uniform float far_plane;

void main() {
    float distance = length(FragPos.xyz - lightPos);
    // Auf [0,1] Bereich normalisieren
    distance = distance / far_plane;
    gl_FragDepth = distance;
}