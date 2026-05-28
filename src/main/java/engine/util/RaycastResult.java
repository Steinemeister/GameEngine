package engine.util;

import org.joml.Vector3i;

public class RaycastResult {
    public boolean hit;               // Wurde überhaupt ein Block getroffen?
    public Vector3i blockPos;         // Globale Block-Koordinate des getroffenen Blocks
    public Vector3i adjacentPos;      // Globale Koordinate des Blocks direkt davor (für Platzieren)
    public int face;                  // Getroffene Würfelseite (0 bis 5)

    public RaycastResult() {
        this.hit = false;
        this.blockPos = new Vector3i();
        this.adjacentPos = new Vector3i();
        this.face = -1;
    }
}
