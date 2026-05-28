#version 330 core
out vec4 FragColor;

in vec2 TexCoords;

uniform sampler2D hudTexture;
uniform vec4 colorModifier;   // Erlaubt das Einfärben oder Ändern der Transparenz
uniform bool useTexture;      // Wenn false, zeichnen wir eine reine Farbfläche

void main() {
    if (useTexture) {
        vec4 texColor = texture(hudTexture, TexCoords);
        // Alpha-Blending: Wenn das Texturpixel unsichtbar ist, verwerfen
        if (texColor.a < 0.05) discard;
        FragColor = texColor * colorModifier;
    } else {
        FragColor = colorModifier;
    }
}