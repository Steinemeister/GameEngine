#version 330 core
out vec4 FragColor;

struct Light {
    vec3 position;
    vec3 color;
};

in vec3 FragPos;
in vec3 Normal;

uniform vec3 viewPos;
uniform Light lights[4]; // Beispiel für 4 Lichter
uniform samplerCube shadowMaps[4]; // Die 4 Cubemaps
uniform float far_plane;

float ShadowCalculation(vec3 fragPos, vec3 lightPos, samplerCube shadowMap) {
    vec3 fragToLight = fragPos - lightPos;
    float closestDepth = texture(shadowMap, fragToLight).r;
    closestDepth *= far_plane;
    float currentDepth = length(fragToLight);

    // Bias gegen Shadow Acne
    float bias = 0.05;
    return currentDepth - bias > closestDepth ? 1.0 : 0.0;
}

void main() {
    vec3 color = vec3(0.1); // Ambienter Grundwert
    vec3 norm = normalize(Normal);

    for(int i = 0; i < 4; i++) {
        // Diffuse Beleuchtung
        vec3 lightDir = normalize(lights[i].position - FragPos);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * lights[i].color;

        // Schattenfaktor berechnen
        float shadow = ShadowCalculation(FragPos, lights[i].position, shadowMaps[i]);

        // Licht kombinieren (Schatten beeinflusst Diffuse und Specular)
        color += (1.0 - shadow) * diffuse;
    }

    FragColor = vec4(color, 1.0);
}