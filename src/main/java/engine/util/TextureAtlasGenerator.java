package engine.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TextureAtlasGenerator {
    public static void generateAtlas(int tileSize) {
        File sourceDir = new File("src/main/resources/textures/blocks");
        File outputDir = new File("src/main/generated");

        // Ordner erstellen, falls sie noch nicht existieren
        if (!outputDir.exists()) outputDir.mkdirs();

        // Alle PNG-Dateien aus dem Block-Ordner holen
        File[] textureFiles = sourceDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (textureFiles == null || textureFiles.length == 0) {
            System.err.println("Warnung: Keine Texturen in src/main/resources/textures/blocks gefunden!");
            return;
        }

        // Alphabetisch sortieren, damit die Reihenfolge (IDs) immer absolut identisch bleibt
        Arrays.sort(textureFiles);

        // Da unser Shader auf ein 4x4 Raster (16 Texturen) ausgelegt ist
        int atlasTilesX = 4;
        int atlasTilesY = 4;
        int atlasWidth = atlasTilesX * tileSize;
        int atlasHeight = atlasTilesY * tileSize;

        // Neuen leeren Bild-Buffer mit Transparenz-Support (ARGB) erstellen
        BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlasImage.createGraphics();

        System.out.println("Generiere Textur-Atlas aus " + textureFiles.length + " Bildern...");

        // Durch das Raster iterieren und die Bilder platzieren
        int fileIndex = 0;
        for (int y = 0; y < atlasTilesY; y++) {
            for (int x = 0; x < atlasTilesX; x++) {
                if (fileIndex < textureFiles.length) {
                    try {
                        // Einzelbild laden
                        BufferedImage tile = ImageIO.read(textureFiles[fileIndex]);

                        // Zielposition im Atlas berechnen
                        int drawX = x * tileSize;
                        int drawY = y * tileSize;

                        // Bild an die exakte Raster-Position zeichnen
                        g2d.drawImage(tile, drawX, drawY, tileSize, tileSize, null);

                        System.out.println("  [ID " + (fileIndex + 1) + "] -> " + textureFiles[fileIndex].getName());
                    } catch (IOException e) {
                        System.err.println("Fehler beim Laden von: " + textureFiles[fileIndex].getName());
                    }
                    fileIndex++;
                }
            }
        }

        g2d.dispose();

        // Den fertigen Atlas abspeichern
        try {
            File outputFile = new File(outputDir, "textureAtlas.png");
            ImageIO.write(atlasImage, "PNG", outputFile);
            System.out.println("Textur-Atlas erfolgreich gespeichert unter: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Speichern des Textur-Atlas", e);
        }
    }
}
