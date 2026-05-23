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

        if (!outputDir.exists()) outputDir.mkdirs();

        File[] textureFiles = sourceDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (textureFiles == null || textureFiles.length == 0) {
            System.err.println("Warnung: Keine Texturen in src/main/resources/textures/blocks gefunden!");
            return;
        }

        Arrays.sort(textureFiles);

        int atlasTilesX = 4;
        int atlasTilesY = 4;
        int atlasWidth = atlasTilesX * tileSize;
        int atlasHeight = atlasTilesY * tileSize;

        BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);

        System.out.println("Generiere 1x1 Textur-Atlas...");

        int fileIndex = 0;
        for (int y = 0; y < atlasTilesY; y++) {
            for (int x = 0; x < atlasTilesX; x++) {
                if (fileIndex < textureFiles.length) {
                    try {
                        BufferedImage tile = ImageIO.read(textureFiles[fileIndex]);

                        // Direkter Pixel-Transfer statt g2d.drawImage (verhindert den Freeze bei 1x1)
                        if (tileSize == 1) {
                            int rgb = tile.getRGB(0, 0);
                            atlasImage.setRGB(x, y, rgb);
                        } else {
                            // Fallback für größere Tiles
                            for (int ty = 0; ty < tileSize; ty++) {
                                for (int tx = 0; tx < tileSize; tx++) {
                                    atlasImage.setRGB(x * tileSize + tx, y * tileSize + ty, tile.getRGB(tx, ty));
                                }
                            }
                        }
                        System.out.println("  [ID " + (fileIndex + 1) + "] -> " + textureFiles[fileIndex].getName());
                    } catch (IOException e) {
                        System.err.println("Fehler bei: " + textureFiles[fileIndex].getName());
                    }
                    fileIndex++;
                }
            }
        }

        try {
            File outputFile = new File(outputDir, "textureAtlas.png");
            ImageIO.write(atlasImage, "PNG", outputFile);
            System.out.println("Atlas erfolgreich gespeichert: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Speichern des Textur-Atlas", e);
        }
    }
}
