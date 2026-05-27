#version 330 core

uniform mat4 invProjection;
uniform mat4 invView;

out vec3 viewDir;

const vec2 positions[4] = vec2[](
vec2(-1.0, -1.0),
vec2( 1.0, -1.0),
vec2(-1.0,  1.0),
vec2( 1.0,  1.0)
);

void main() {
    vec2 pos = positions[gl_VertexID];
    gl_Position = vec4(pos, 1.0, 1.0); // z = 1.0 zwingt das Quad ganz nach hinten

    // Berechne die Blickrichtung im Weltraum für das Fragment
    vec4 unprojected = invProjection * vec4(pos, 1.0, 1.0);
    vec4 worldPos = invView * vec4(unprojected.xyz, 0.0); // w = 0 isoliert die Rotation (keine Bewegung)
    viewDir = worldPos.xyz;
}