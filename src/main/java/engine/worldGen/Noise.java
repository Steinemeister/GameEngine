package engine.worldGen;

public class Noise {
    private final long seed;

    // Bit-Konstanten für den K.jpg-Primzahl-Hash
    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;

    // 2D Schiefstellungskonstanten
    private static final double SKEW_2D = 0.366025403784439;    // (Math.sqrt(3.0) - 1.0) / 2.0
    private static final double UNSKEW_2D = -0.211324865405187; // (1.0 / Math.sqrt(3.0) - 1.0) / 2.0

    // 3D Schiefstellungskonstanten
    private static final double SKEW_3D = 1.0 / 3.0;
    private static final double UNSKEW_3D = -1.0 / 6.0;

    public Noise(long seed) {
        this.seed = seed;
    }

    // =========================================================================
    // 2D OPENSIMPLEX2S NOISE & FBM (Für das Gelände)
    // =========================================================================

    public double openSimplex2D(double x, double y) {
        double skew = (x + y) * SKEW_2D;
        int i = fastFloor(x + skew);
        int j = fastFloor(y + skew);

        double unskew = (i + j) * UNSKEW_2D;
        double x0 = x - (i + unskew);
        double y0 = y - (j + unskew);

        double value = 0;

        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            t0 *= t0;
            value += t0 * t0 * extrapolate2D(i, j, x0, y0);
        }

        int i1 = (x0 > y0) ? 1 : 0;
        int j1 = (x0 > y0) ? 0 : 1;

        double x1 = x0 - i1 - UNSKEW_2D;
        double y1 = y0 - j1 - UNSKEW_2D;
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            t1 *= t1;
            value += t1 * t1 * extrapolate2D(i + i1, j + j1, x1, y1);
        }

        double x2 = x0 - 1.0 - 2.0 * UNSKEW_2D;
        double y2 = y0 - 1.0 - 2.0 * UNSKEW_2D;
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            t2 *= t2;
            value += t2 * t2 * extrapolate2D(i + 1, j + 1, x2, y2);
        }

        return value * 99.20433458271871;
    }

    public double openSimplex2DFbm(double x, double y, int octaves, double lacunarity, double gain) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxValue = 0.0;

        for (int i = 0; i < octaves; i++) {
            total += openSimplex2D(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return total / maxValue;
    }

    // =========================================================================
    // 3D OPENSIMPLEX2S NOISE (Für Spaghetti-Höhlen)
    // =========================================================================

    /**
     * Berechnet den 3D OpenSimplex2S Wert mittels Primzahl-Hashes.
     * @return Ein Wert zwischen -1.0 und 1.0
     */
    public double openSimplex(double x, double y, double z) {
        double skew = (x + y + z) * SKEW_3D;
        int i = fastFloor(x + skew);
        int j = fastFloor(y + skew);
        int k = fastFloor(z + skew);

        double unskew = (i + j + k) * UNSKEW_3D;
        double x0 = x - (i + unskew);
        double y0 = y - (j + unskew);
        double z0 = z - (k + unskew);

        double value = 0;

        // Ecke (0,0,0)
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0) {
            t0 *= t0;
            value += t0 * t0 * extrapolate3D(i, j, k, x0, y0, z0);
        }

        // Bestimme die inneren Räume des Simplex-Gitters
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=1; k2=0; }
            else if (x0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=0; k2=1; }
            else { i1=0; j1=0; k1=1; i2=1; j2=0; k2=1; }
        } else {
            if (y0 < z0) { i1=0; j1=0; k1=1; i2=0; j2=1; k2=1; }
            else if (x0 < z0) { i1=0; j1=1; k1=0; i2=0; j2=1; k2=1; }
            else { i1=0; j1=1; k1=0; i2=1; j2=1; k2=0; }
        }

        // Ecke 1
        double x1 = x0 - i1 - UNSKEW_3D;
        double y1 = y0 - j1 - UNSKEW_3D;
        double z1 = z0 - k1 - UNSKEW_3D;
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 > 0) {
            t1 *= t1;
            value += t1 * t1 * extrapolate3D(i + i1, j + j1, k + k1, x1, y1, z1);
        }

        // Ecke 2
        double x2 = x0 - i2 - 2.0 * UNSKEW_3D;
        double y2 = y0 - j2 - 2.0 * UNSKEW_3D;
        double z2 = z0 - k2 - 2.0 * UNSKEW_3D;
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 > 0) {
            t2 *= t2;
            value += t2 * t2 * extrapolate3D(i + i2, j + j2, k + k2, x2, y2, z2);
        }

        // Ecke 3 (1,1,1)
        double x3 = x0 - 1.0 - 3.0 * UNSKEW_3D;
        double y3 = y0 - 1.0 - 3.0 * UNSKEW_3D;
        double z3 = z0 - 1.0 - 3.0 * UNSKEW_3D;
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 > 0) {
            t3 *= t3;
            value += t3 * t3 * extrapolate3D(i + 1, j + 1, k + 1, x3, y3, z3);
        }

        return value * 32.0; // Normalisierungsfaktor für 3D
    }

    // =========================================================================
    // INTERNE HASHER
    // =========================================================================

    private double extrapolate2D(int gridX, int gridY, double dx, double dy) {
        long hash = seed ^ (gridX * PRIME_X) ^ (gridY * PRIME_Y);
        hash *= HASH_MULTIPLIER;
        hash ^= (hash >>> 31);

        int index = (int) (hash & 0x07);
        switch (index) {
            case 0: return dx + dy;
            case 1: return -dx + dy;
            case 2: return dx - dy;
            case 3: return -dx - dy;
            case 4: return dx * 2.0;
            case 5: return -dx * 2.0;
            case 6: return dy * 2.0;
            case 7: return -dy * 2.0;
            default: return 0;
        }
    }

    private double extrapolate3D(int gridX, int gridY, int gridZ, double dx, double dy, double dz) {
        long hash = seed ^ (gridX * PRIME_X) ^ (gridY * PRIME_Y) ^ (gridZ * PRIME_Z);
        hash *= HASH_MULTIPLIER;
        hash ^= (hash >>> 31);

        // 24 standardisierte 3D-Gradientenrichtungen (X,Y,Z Kombinationen)
        int index = (int) ((hash & 0x0F00) >>> 8) % 12;
        switch (index) {
            case 0:  return  dx + dy;
            case 1:  return -dx + dy;
            case 2:  return  dx - dy;
            case 3:  return -dx - dy;
            case 4:  return  dx + dz;
            case 5:  return -dx + dz;
            case 6:  return  dx - dz;
            case 7:  return -dx - dz;
            case 8:  return  dy + dz;
            case 9:  return -dy + dz;
            case 10: return  dy - dz;
            case 11: return -dy - dz;
            default: return 0;
        }
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}