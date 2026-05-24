package engine.worldGen;

public class WorldGen {
    public static final byte BLOCK_AIR = 0;
    public static final byte BLOCK_GRASS = 1;
    public static final byte BLOCK_DIRT = 2;
    public static final byte BLOCK_STONE = 3;
    public static final byte BLOCK_WATER = 4; // Optional: Falls du Wasser hinzufügen willst

    private static final int MAX_HEIGHT = 256;
    private static final int WATER_LEVEL = 62; // Höhe des Meeresspiegels
    private static final int BASE_HEIGHT = 64;  // Mittlere Höhe der Welt

    private final Noise noise;

    public WorldGen(long seed) {
        this.noise = new Noise(seed);
    }

    /**
     * Generiert ein komplexes, unendliches Terrain.
     * 100% deterministisch und hochoptimiert.
     */
    public byte getBlockAt(int globalX, int globalY, int globalZ) {
        if (globalY >= MAX_HEIGHT) {
            return BLOCK_AIR;
        }

        // 2. CONTINUOUS NOISE-ABFRAGEN FÜR DAS GELÄNDE (2D)
        double continentalNoise = noise.openSimplex2DFbm(globalX * 0.001, globalZ * 0.001, 2, 2.0, 0.4);
        double detailNoise = noise.openSimplex2DFbm(globalX * 0.008, globalZ * 0.008, 4, 2.0, 0.45);

        // 3. GLÄTTUNG DER ÜBERGÄNGE (Lerp + Smoothstep)
        double mountainHeight = (continentalNoise * 35.0) + (detailNoise * 20.0);
        double valleyHeight   = (continentalNoise * 15.0) + (detailNoise * 6.0);

        double t = (continentalNoise + 1.0) * 0.5;
        t = t * t * (3.0 - 2.0 * t);

        double finalHeightModifier = valleyHeight + t * (mountainHeight - valleyHeight);
        int surfaceHeight = BASE_HEIGHT + (int) Math.round(finalHeightModifier);

        // 4. STRUKTUR-ZUWEISUNG ANHAND DER ABSOLUTEN HÖHE (globalY)

        // A: Über der Erdoberfläche (Himmel oder Ozean-Wasser)
        if (globalY > surfaceHeight) {
            if (globalY <= WATER_LEVEL) {
                return BLOCK_WATER;
            }
            return BLOCK_AIR;
        }

        // B: Exakt auf der Erdoberfläche
        if (globalY == surfaceHeight) {
            return (globalY <= WATER_LEVEL + 1) ? BLOCK_DIRT : BLOCK_GRASS;
        }

        // C: Die Erdschicht direkt unter dem Oberflächenblock
        if (globalY > surfaceHeight - 4) {
            return BLOCK_DIRT;
        }

        // D: DIE TIEFE ERDKRUSTE (STEIN & SPAGHETTI-HÖHLEN)
        if (globalY < (surfaceHeight - 5)) {

            // --- HÖHLEN-PROFIL (VERTIKALE STEUERUNG) ---
            double caveCenterHeight = -30.0; // Zentrum der Höhlenaktivität
            double caveZoneWidth = 150.0;    // Vertikale Spannweite der Höhlen

            double distanceToCenter = Math.abs(globalY - caveCenterHeight);
            double caveSizeFactor = 1.0 - (distanceToCenter / caveZoneWidth);
            if (caveSizeFactor < 0.0) caveSizeFactor = 0.0;

            // Smoothstep für sanftes Ausblenden nach oben/unten
            caveSizeFactor = caveSizeFactor * caveSizeFactor * (3.0 - 2.0 * caveSizeFactor);

            if (caveSizeFactor > 0.01) {
                // Skalierung für die Höhlendichte (Frequenz)
                // Y wird leicht gestaucht, damit die Tunnel seltener steil nach unten abstürzen
                double scale3D = 0.022;
                double n3d = noise.openSimplex(globalX * scale3D, globalY * 0.015, globalZ * scale3D);

                // RIDGE-NOISE MATHEMATIK:
                // Wir nehmen den Absolutwert. Dadurch wird die Null-Linie zu einem scharfen "Tal" (Wert 0.0).
                // Werte nahe 0.0 bilden das exakte mathematische Zentrum eines Tunnels.
                double spaghettiNoise = Math.abs(n3d);

                // Bestimme die Dicke des Tunnels basierend auf dem Höhenprofil.
                // Im Zentrum (caveSizeFactor=1) erlauben wir Werte bis 0.07 (breite, voluminöse Tunnel).
                // Nahe der Oberfläche sinkt das Limit auf 0.015 (sehr dünne Kriechgänge).
                double tunnelThickness = 0.01 + (caveSizeFactor * 0.06);

                if (spaghettiNoise < tunnelThickness) {
                    return BLOCK_AIR; // Ein wunderschöner, runder Höhlengang entsteht!
                }
            }
        }

        return BLOCK_STONE;
    }
}
