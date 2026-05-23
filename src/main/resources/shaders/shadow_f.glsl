#version 330 core
in vec3 WorldPosOut;

uniform vec3 lightPos;
uniform float far_plane;

void main() {
    float lightDistance = length(WorldPosOut - lightPos);
    lightDistance = lightDistance / far_plane;
    gl_FragDepth = lightDistance;
}