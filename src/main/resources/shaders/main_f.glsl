#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 Normal;
flat in uint BlockID;

uniform sampler2D textureAtlas;
uniform vec3 lightPos;

void main() {
    // 1. Die Raster-Größe deines Atlasses definieren (4x4 = 4.0)
    float atlasSize = 4.0;

    // 2. BlockID zu einem nullbasierten Index konvertieren (Luft = 0 wird im Vertex-Shader verworfen)
    int atlasIndex = int(BlockID) - 1;
    if (atlasIndex < 0) atlasIndex = 0;

    // 3. Berechne die Spalte (U) und Zeile (V) im Atlas-Raster
    float uOffset = float(atlasIndex % 4) / atlasSize;
    float vOffset = float(atlasIndex / 4) / atlasSize;

    // 4. Skaliere die 0-1 Koordinate auf 1/4 der Größe und addiere den Offset
    // Wichtig: In OpenGL liegt der Textur-Ursprung (0,0) unten links!
    // Falls deine Textur auf dem Kopf steht, ändern wir das '+' bei vOffset später zu einem '-'
    vec2 atlasTexCoords = (TexCoords / atlasSize) + vec2(uOffset, vOffset);

    // 5. Farbe aus dem Atlas-Ausschnitt lesen
    vec3 texColor = texture(textureAtlas, atlasTexCoords).rgb;

    // --- Deine funktionierende Beleuchtung ---
    vec3 ambient = 0.2 * texColor;
    vec3 normal = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * vec3(1.0);

    FragColor = vec4(ambient + diffuse * texColor, 1.0);
}