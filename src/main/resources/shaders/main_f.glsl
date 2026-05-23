#version 330 core

out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;

struct PointLight {
    vec3 position;
    vec3 color;
    samplerCube shadowMap;
};

#define MAX_LIGHTS 1 // Passe dies an die Anzahl deiner Lichter an

uniform PointLight lights[MAX_LIGHTS];
uniform sampler2D diffuseTexture;
uniform float far_plane;

// Funktion zur Berechnung des Schattens für eine Cube Map
float ShadowCalculation(vec3 fragPos, PointLight light) {
    vec3 fragToLight = fragPos - light.position;

    // Den nächstgelegenen Tiefenwert aus der Shadow Map auslesen
    float closestDepth = texture(light.shadowMap, fragToLight).r;

    // Den Wert wieder in den echten Abstand (Weltkoordinaten) umrechnen
    closestDepth *= far_plane;

    // Aktueller Abstand zwischen Fragment und Licht
    float currentDepth = length(fragToLight);

    // Ein kleiner Bias verhindert "Shadow Acne" (Musterbildung auf Oberflächen)
    float bias = 0.05;
    float shadow = currentDepth - bias > closestDepth ? 1.0 : 0.0;

    return shadow;
}

void main() {
    vec3 color = texture(diffuseTexture, TexCoords).rgb;
    vec3 normal = normalize(Normal);

    // Ambienter Lichtanteil (Grundbeleuchtung)
    vec3 ambient = 0.15 * color;

    vec3 lighting = vec3(0.0);

    for(int i = 0; i < MAX_LIGHTS; i++) {
        // Diffuser Lichtanteil
        vec3 lightDir = normalize(lights[i].position - FragPos);
        float diff = max(dot(normal, lightDir), 0.0);
        vec3 diffuse = diff * lights[i].color;

        // Schatten berechnen
        float shadow = ShadowCalculation(FragPos, lights[i]);

        // Diffusen Anteil mit Schatten verrechnen (wenn shadow == 1, bleibt nur Ambient)
        lighting += (ambient + (1.0 - shadow) * diffuse) * color;
    }

    FragColor = vec4(lighting, 1.0);
}