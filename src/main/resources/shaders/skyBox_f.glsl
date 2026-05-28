#version 330 core
out vec4 FragColor;

in vec3 viewDir; // Vom neuen Vertex Shader

// Inputs von deiner Applikation
uniform vec3 sunDirection; // Nutzt jetzt den Namen aus deiner Engine
uniform vec3 sunColor;     // Nutzt jetzt die Lichtfarbe deiner Engine
uniform float time;        // Zeit-Uniform (In Java: glfwGetTime())
uniform float auroraActivity;

// Statischer Fallback-Seed, falls von Java kein Vec2 übergeben wird
const vec2 seed = vec2(1337.0, 42.0);

// =========================================================================
// PHYSIKALISCHE & KOSMISCHE PARAMETER
// =========================================================================
const vec3 SUN_BASE_COLOR     = vec3(1.0, 0.96, 0.88);
const vec3 ATMOSPHERE_SCATTER = vec3(0.16, 0.40, 0.95);
const float MOON_ALBEDO        = 0.25;

const vec3 SPACE_COLOR_TOP    = vec3(0.001, 0.002, 0.005);
const vec3 SPACE_COLOR_HORIZ  = vec3(0.002, 0.004, 0.009);

const float STAR_DENSITY       = 0.994;
const float STAR_TWINKLE_SPEED = 2.5;
const float AURORA_SPEED       = 0.18;

// =========================================================================

// --- Hilfsfunktionen für Seed-basierten Zufall und Rauschen ---
float hash3D(vec3 p) {
    p += vec3(seed.x * 0.001, seed.y * 0.001, (seed.x + seed.y) * 0.0005);
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p.xyz, p.yzx + 19.19);
    return fract(p.x * p.y * p.z);
}

float noise2D(vec2 p) {
    p += vec2(sin(seed.x * 0.01), cos(seed.y * 0.01));
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7)) + seed.x * 0.1) * (43758.5453 + seed.y * 0.01));
    float b = fract(sin(dot(i + vec2(1.0, 0.0), vec2(127.1, 311.7)) + seed.x * 0.1) * (43758.5453 + seed.y * 0.01));
    float c = fract(sin(dot(i + vec2(0.0, 1.0), vec2(127.1, 311.7)) + seed.x * 0.1) * (43758.5453 + seed.y * 0.01));
    float d = fract(sin(dot(i + vec2(1.0, 1.0), vec2(127.1, 311.7)) + seed.x * 0.1) * (43758.5453 + seed.y * 0.01));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0; float a = 0.5; vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 4; ++i) {
        v += a * noise2D(p); p = rot * p * 2.0 + shift; a *= 0.5;
    }
    return v;
}

// 1. Himmel-Grundfarbe (Physikalische Streuung)
vec3 calculateSkyColor(vec3 dir, vec3 lightDir, vec3 currentSunColor, float dayFactor, float sunsetFactor, float nightFactor, float horizonFactor, float solarEclipse) {
    vec3 daySkyTop = currentSunColor * ATMOSPHERE_SCATTER * 0.5;
    vec3 daySkyHoriz = currentSunColor * mix(ATMOSPHERE_SCATTER, vec3(1.0), 0.5) * 0.8;

    vec3 skyTop = mix(SPACE_COLOR_TOP, daySkyTop, dayFactor);
    vec3 skyHoriz = mix(SPACE_COLOR_HORIZ, daySkyHoriz, dayFactor);
    vec3 sky = mix(skyHoriz, skyTop, horizonFactor);

    float sunHorizDot = max(dot(dir, lightDir), 0.0);
    float sunsetGlow = pow(sunHorizDot, 4.0) * (1.0 - horizonFactor) * (dayFactor + sunsetFactor * 0.5);
    sky += currentSunColor * sunsetGlow * 0.4;

    return mix(sky, SPACE_COLOR_TOP * 0.5, solarEclipse);
}

// 2. Sonne mit Atmosphären-Filterung (Extinktion) & Eklipsen-Korona
vec3 calculateSun(vec3 dir, vec3 lightDir, float sunHeight, float dayFactor, float sunsetFactor, float nightFactor, float solarEclipse, out vec3 currentSunColor) {
    float opticalDepth = 1.0 / (max(sunHeight, 0.01) + 0.08);
    vec3 extinction = exp(-ATMOSPHERE_SCATTER * opticalDepth * 0.35);
    currentSunColor = SUN_BASE_COLOR * extinction;

    if (sunHeight <= -0.1) return vec3(0.0);

    float cosTheta = dot(dir, lightDir);
    float glowExponent = mix(120.0, 15.0, sunsetFactor);
    float glow = pow(max(cosTheta, 0.0), glowExponent) * mix(0.5, 1.5, sunsetFactor) * (1.0 - nightFactor);
    float sunDisc = smoothstep(0.998, 0.999, cosTheta) * 2.0 * (1.0 - nightFactor);

    sunDisc *= (1.0 - solarEclipse);

    vec3 dynamicCoronaColor = currentSunColor * vec3(1.1, 0.85, 0.6);
    float eclipseGlow = pow(max(cosTheta, 0.0), 400.0) * 12.0 * solarEclipse;

    return (currentSunColor * (sunDisc + glow)) + (dynamicCoronaColor * eclipseGlow);
}

// 3. Mond mit Atmosphären-Einfluss, Kratern, Phasen & Blutmond-Finsternis
vec3 calculateMoon(vec3 dir, vec3 mDir, vec3 lightDir, float solarEclipse, float lunarEclipse) {
    if (mDir.y <= -0.1) return vec3(0.0);

    float cosTheta = dot(dir, mDir);
    if (cosTheta < 0.997) return vec3(0.0);

    float moonDisc = smoothstep(0.997, 0.998, cosTheta);

    float phase = max(dot(dir, lightDir), 0.0);
    phase = mix(0.02, 1.0, phase);

    float moonOpticalDepth = 1.0 / (max(mDir.y, 0.01) + 0.08);
    vec3 moonExtinction = exp(-ATMOSPHERE_SCATTER * moonOpticalDepth * 0.35);
    vec3 dynamicMoonColor = SUN_BASE_COLOR * moonExtinction * MOON_ALBEDO;

    vec3 dynamicEclipseColor = SUN_BASE_COLOR * exp(-ATMOSPHERE_SCATTER * 8.0) * 2.5;
    vec3 baseColor = mix(dynamicMoonColor, dynamicEclipseColor, lunarEclipse * moonDisc);

    float surfaceNoise = noise2D(dir.xy * 25.0) * 0.25 + 0.75;
    vec3 finalMoonColor = baseColor * surfaceNoise * phase;

    finalMoonColor *= (1.0 - solarEclipse);

    return finalMoonColor * moonDisc;
}

// 4. Sterne (Inklusive Flimmern)
vec3 calculateStars(vec3 dir, vec3 lightDir, float nightFactor, float sunsetFactor, float horizonFactor, float solarEclipse) {
    if (dir.y <= 0.0) return vec3(0.0);

    vec3 starGrid = floor(dir * 200.0);
    float starHash = hash3D(starGrid);

    if (starHash > STAR_DENSITY) {
        float starIntensity = fract(starHash * 10.0);
        float twinkle = sin(time * STAR_TWINKLE_SPEED + starHash * 50.0) * 0.4 + 0.6;

        float sunOppositeFactor = clamp(-dot(dir, lightDir), 0.0, 1.0);
        float starVisibility = max(max(nightFactor, sunsetFactor * sunOppositeFactor * 0.7), solarEclipse);

        return vec3(starIntensity * twinkle * starVisibility * pow(horizonFactor, 3.0));
    }
    return vec3(0.0);
}

// 5. Prozedurale Milchstraße (Vervollständigt)
vec3 calculateMilkyWay(vec3 dir, float nightFactor, float solarEclipse) {
    float visibility = max(nightFactor, solarEclipse);
    if (dir.y <= -0.05 || visibility < 0.01) return vec3(0.0);

    // Kosmisches Band um ca. 60 Grad neigen, damit es organisch über den Himmel verläuft
    vec3 rotatedDir = dir;
    float angle = 1.05;
    float s = sin(angle); float c = cos(angle);
    rotatedDir.x = dir.x * c - dir.z * s;
    rotatedDir.z = dir.x * s + dir.z * c;

    // UV-Koordinaten für das kosmische Band berechnen
    vec2 galaxyUV = vec2(atan(rotatedDir.x, rotatedDir.z) * 1.5, rotatedDir.y * 3.0);

    // Berechne die Breite und Intensität des galaktischen Bandes
    float band = smoothstep(0.6, 0.0, abs(rotatedDir.y - 0.12));
    if (band <= 0.0) return vec3(0.0);

    // Fraktales Rauschen (FBM) simuliert dunkle Staubwolken und helle Sternencluster
    float dust = fbm(galaxyUV * 2.5);

    // Violett-bläulicher Grundton gemischt mit hellen Gaswolken-Zentren
    vec3 galaxyColor = mix(vec3(0.05, 0.03, 0.12), vec3(0.25, 0.18, 0.35), dust);

    // Extra Helligkeits-Core in der Mitte des Bandes
    galaxyColor += vec3(0.15, 0.12, 0.20) * pow(band, 3.0);

    // Am Horizont leicht ausfaden lassen, um harten Kanten am Boden vorzubeugen
    float horizonFade = smoothstep(-0.05, 0.15, dir.y);

    return galaxyColor * band * visibility * horizonFade * 0.45;
}

// 6. Prozedurale Polarlichter / Nordlichter (Hinzugefügt)
vec3 calculateAurora(vec3 dir, float nightFactor) {
    if (dir.y <= 0.02 || nightFactor < 0.01 || auroraActivity <= 0.001) return vec3(0.0);

    vec3 totalAurora = vec3(0.0);
    float alphaAcc = 0.0; // Speicher für die Lichtdichte (verhindert Überstrahlung)

    // High-Fidelity Raymarching-Parameter
    const int steps = 24;          // Mehr Schritte für extrem feine Strukturen
    float baseHeight = 200.0;       // Einstiegshöhe in die Ionosphäre
    float layerThickness = 2.0;    // Dünnere Schichten für weichere Übergänge

    // Strahl-Zufallsversatz (Dithering) gegen sichtbare Schicht-Kanten (Banding-Artefakte)
    float jitter = hash3D(dir * 100.0 + vec3(time)) * 0.5;

    for (int i = 0; i < steps; i++) {
        if (alphaAcc >= 0.95) break; // Frühzeitiger Abbruch, wenn das Licht deckend ist

        float currentHeight = baseHeight + (float(i) + jitter) * layerThickness;
        float distanceToLayer = currentHeight / dir.y;
        vec3 samplePos = dir * distanceToLayer;

        // Welt-Koordinaten für die Vorhang-Simulation
        vec2 uv = samplePos.xz * 0.0025;

        // 1. DOMAIN WARPING: Verzieht die Aurora in organische, magnetische Wellenformen
        float timeSpeed = time * AURORA_SPEED;
        vec2 warp = vec2(
        noise2D(uv * 1.0 + vec2(timeSpeed * 0.5, timeSpeed)),
        noise2D(uv * 1.5 - vec2(timeSpeed, timeSpeed * 0.3))
        ) * 0.4; // Stärke der Verwirbelung

        vec2 warpedUV = uv + warp;

        // 2. HAUPTBAND (Der dicke leuchtende Vorhang)
        float mainCurtain = smoothstep(0.3, 0.7, sin(warpedUV.x * 2.0 + warpedUV.y * 1.0));

        // 3. RAY RAVELING (Senkrechte feine Filament-Streifen entgegengesetzt zur Windrichtung)
        // Wir strecken das Rauschen extrem auf einer Achse, um Feldlinien zu imitieren
        float filaments = noise2D(vec2(warpedUV.x * 35.0, warpedUV.y * 3.0 - timeSpeed * 2.0));
        filaments = pow(filaments, 2.0) * 1.8; // Schärft die Streifen nach

        // Kombination aus Vorhang-Form und feinen Streifen
        float density = mainCurtain * filaments * (fbm(uv * 4.0) * 0.6 + 0.4);

        // Vertikale Dichteverteilung (Unten scharfkantig, nach oben hin verblasst es)
        float progress = float(i) / float(steps);
        float verticalProfile = sin(progress * 3.1415) * (1.0 - progress * 0.5);

        density *= verticalProfile * 0.18;

        if (density > 0.001) {
            // 4. REALISTISCHES FARBSPEKTRUM (Sauerstoff-Grün an der Basis, Stickstoff-Violett/Rot an den Spitzen)
            vec3 greenBase = vec3(0.02, 0.95, 0.35);
            vec3 purpleTop = vec3(0.75, 0.05, 0.85);

            // Reines Gas-Glühen: Je dichter das Filament, desto intensiver die Farbe
            vec3 layerColor = mix(greenBase, purpleTop, pow(progress, 1.5));
            layerColor += vec3(0.1, 0.4, 0.9) * (1.0 - mainCurtain) * 0.1; // Sanftes blaues Umgebungsleuchten

            // Physikalische Akkumulation (Beer-Lambert-Gesetz angenähert)
            float alpha = density * (1.0 - alphaAcc);
            totalAurora += layerColor * alpha * 2.2; // 2.2 = Helligkeits-Booster
            alphaAcc += alpha;
        }
    }

    // Dynamischer Höhen-Fade verhindert das Abschneiden am Horizont
    float horizonFade = smoothstep(0.02, 0.25, dir.y);

    return totalAurora * nightFactor * horizonFade;
}

void main() {
    vec3 dir = normalize(viewDir);

    // Die Lichtrichtung zeigt bei dir nach UNTEN (-sunDirection), wir brauchen Vektor zur Sonne
    vec3 lightDir = normalize(-sunDirection);
    // Mond steht der Sonne exakt gegenüber
    vec3 moonDir = -lightDir;

    float sunHeight = lightDir.y;

    // Phasen-Faktoren für weiche Himmelsübergänge berechnen
    float dayFactor = clamp(sunHeight * 4.0, 0.0, 1.0);
    float sunsetFactor = clamp((1.0 - abs(sunHeight)) * 3.0, 0.0, 1.0) * (1.0 - dayFactor);
    float nightFactor = clamp(-sunHeight * 4.0, 0.0, 1.0);
    float horizonFactor = clamp(dir.y, 0.0, 1.0);

    // Finsternis-Parameter (Können bei Bedarf als Uniform übergeben werden, hier 0.0)
    float solarEclipse = 0.0;
    float lunarEclipse = 0.0;

    // 1. Sonne & Himmelsgrundfarbe ermitteln
    vec3 currentSunColor;
    vec3 sunOutput = calculateSun(dir, lightDir, sunHeight, dayFactor, sunsetFactor, nightFactor, solarEclipse, currentSunColor);

    // Die berechnete Himmelsfarbe nutzt das durch die Atmosphäre gefilterte Sonnenlicht
    vec3 skyOutput = calculateSkyColor(dir, lightDir, currentSunColor, dayFactor, sunsetFactor, nightFactor, horizonFactor, solarEclipse);

    // 2. Kosmische Layer berechnen
    vec3 moonOutput = calculateMoon(dir, moonDir, lightDir, solarEclipse, lunarEclipse);
    vec3 starsOutput = calculateStars(dir, lightDir, nightFactor, sunsetFactor, horizonFactor, solarEclipse);
    vec3 milkyWayOutput = calculateMilkyWay(dir, nightFactor, solarEclipse);
    vec3 auroraOutput = calculateAurora(dir, nightFactor) * auroraActivity;

    // 3. Alle Layer mathematisch korrekt mischen
    vec3 finalColor = skyOutput + sunOutput + moonOutput + starsOutput + milkyWayOutput + auroraOutput;

    FragColor = vec4(finalColor, 1.0);
}