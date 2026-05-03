#version 330 core
out vec4 FragColor;

struct Light {
    vec3 position;
    vec3 color;
    samplerCube shadowMap;
};

in vec3 FragPos;
in vec3 Normal;

uniform vec3 viewPos;
uniform Light lights[1];
uniform float far_plane;

float calculateShadow(vec3 fragPos, Light light) {
    vec3 fragToLight = fragPos - light.position;
    float currentDepth = length(fragToLight);

    // Aus der Cubemap lesen (Normalisiert 0.0 bis 1.0)
    float closestDepth = texture(light.shadowMap, fragToLight).r;

    // Zurück auf Welt-Einheiten skalieren
    closestDepth *= far_plane;


    // Bias gegen Shadow Acne
    float bias = 0.05;
    return (currentDepth - bias) > closestDepth ? 1.0 : 0.0;
}

void main() {
    vec3 norm = normalize(Normal);
    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 totalLight = vec3(0.0);

    // Test-Ambient, damit es nie ganz schwarz ist
    vec3 ambient = vec3(0.1);

    for(int i = 0; i < 1; i++) {
        vec3 lightDir = normalize(lights[i].position - FragPos);

        vec3 halfwayDir = normalize(lightDir + viewDir);

        //specular
        float shininess = 32.0; // Höherer Wert = kleinerer, schärferer Glanzpunkt
        float spec = pow(max(dot(norm, halfwayDir), 0.0), shininess);
        vec3 specular = spec * lights[i].color;

        // Diffuse
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * lights[i].color;

        float distance = length(lights[i].position - FragPos);
        float attenuation = 4.0 / (distance); // Physikalischer Abfall

        // Schatten
        float shadow = calculateShadow(FragPos, lights[i]);

        totalLight += ambient + (1.0 - shadow) * (diffuse + specular) * attenuation;
    }

    // Gamma-Korrektur (optional, hilft gegen zu dunkle Bilder)
    totalLight = pow(totalLight, vec3(1.0/2.2));

    FragColor = vec4(totalLight, 1.0);

    //FragColor = vec4(1.0);
}