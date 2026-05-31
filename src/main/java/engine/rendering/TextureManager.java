package engine.rendering;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.*;

public class TextureManager {
    private final Map<String, Integer> textures = new HashMap<>();

    /**
     * Lädt eine Textur in den VRAM und speichert die ID.
     * DARF NUR IM RENDER-THREAD LAUFEN!
     */
    public int loadTexture(String filePath) {
        if (textures.containsKey(filePath)) {
            return textures.get(filePath);
        }

        int width, height;
        ByteBuffer image;

        // Nutzt den schnellen LWJGL MemoryStack für C-Pointer (width, height, channels)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // Bild laden via stb_image
            image = stbi_load(filePath, w, h, comp, 4);
            if (image == null) {
                throw new RuntimeException("Textur-Datei nicht gefunden oder lesbar: " + filePath + " | " + stbi_failure_reason());
            }
            width = w.get();
            height = h.get();
        }

        // OpenGL Textur generieren
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Textur-Filterung für Voxel/PixelArt (Scharfe Kanten ohne Matsch)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Pixel-Daten in die Grafikkarte hochladen
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);

        // Speicher des CPU-Bildes wieder freigeben
        stbi_image_free(image);

        textures.put(filePath, textureId);
        return textureId;
    }

    public void cleanup() {
        for (int id : textures.values()) {
            glDeleteTextures(id);
        }
        textures.clear();
    }
}
