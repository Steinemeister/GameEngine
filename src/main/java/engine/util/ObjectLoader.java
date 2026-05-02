package engine.util;

import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class ObjectLoader {


    private record VertexKey(int pIdx, int tIdx, int nIdx) {}

    public static Mesh loadOBJ(String filePath) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<float[]> finalVertices = new ArrayList<>();
        List<Integer> finalIndices = new ArrayList<>();
        Map<VertexKey, Integer> vertexCache = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length < 2) continue;

                switch (tokens[0]) {
                    case "v"  -> positions.add(new float[]{Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])});
                    case "vt" -> texCoords.add(new float[]{Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2])});
                    case "vn" -> normals.add(new float[]{Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])});
                    case "f"  -> processFace(tokens, finalVertices, finalIndices, vertexCache, positions, texCoords, normals);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Laden der OBJ-Datei", e);
        }

        return createMesh(finalVertices, finalIndices);
    }

    private static void processFace(String[] tokens, List<float[]> outVertices, List<Integer> outIndices,
                                    Map<VertexKey, Integer> cache, List<float[]> p, List<float[]> tc, List<float[]> n) {
        for (int i = 1; i <= 3; i++) {
            String[] parts = tokens[i].split("/", -1);

            int pIdx = Integer.parseInt(parts[0]);
            int tIdx = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1]) : -1;
            int nIdx = (parts.length > 2 && !parts[2].isEmpty()) ? Integer.parseInt(parts[2]) : -1;

            VertexKey key = new VertexKey(pIdx, tIdx, nIdx);

            if (!cache.containsKey(key)) {
                float[] pos = p.get(pIdx - 1);
                float[] tex = (tIdx != -1) ? tc.get(tIdx - 1) : new float[]{0.0f, 0.0f};
                float[] norm = (nIdx != -1) ? n.get(nIdx - 1) : new float[]{0.0f, 1.0f, 0.0f};

                outVertices.add(new float[]{pos[0], pos[1], pos[2], tex[0], tex[1], norm[0], norm[1], norm[2]});
                cache.put(key, outVertices.size() - 1);
            }
            outIndices.add(cache.get(key));
        }
    }

    private static Mesh createMesh(List<float[]> vertices, List<Integer> indices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Buffer befüllen
            FloatBuffer vBuffer = stack.mallocFloat(vertices.size() * 8);
            for (float[] v : vertices) vBuffer.put(v);
            vBuffer.flip();

            IntBuffer iBuffer = stack.mallocInt(indices.size());
            for (int i : indices) iBuffer.put(i);
            iBuffer.flip();

            // OpenGL Setup
            int vao = glGenVertexArrays();
            int vbo = glGenBuffers();
            int ebo = glGenBuffers();

            glBindVertexArray(vao);

            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuffer, GL_STATIC_DRAW);

            int stride = 8 * 4;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);  // Position
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12); // UV
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 20); // Normals
            glEnableVertexAttribArray(2);

            glBindVertexArray(0);
            return new Mesh(vao, vbo, ebo, indices.size());
        }
    }
}
