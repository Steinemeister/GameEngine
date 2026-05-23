#version 330 core
in vec4 FragPos;

uniform vec3 lightPos;
uniform float far_plane;

void main() {
    // Abstand zwischen Fragment und Lichtquelle berechnen
    float lightDistance = length(FragPos.xyz - lightPos);

    // Den Abstand in den Bereich [0, 1] skalieren, indem wir durch far_plane teilen
    lightDistance = lightDistance / far_plane;

    // Diesen Wert als Tiefenwert in die Shadow Map schreiben
    gl_FragDepth = lightDistance;
}