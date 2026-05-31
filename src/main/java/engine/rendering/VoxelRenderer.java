package engine.rendering;

import engine.exchange.EngineData;
import engine.exchange.EngineTask;
import engine.exchange.EngineTaskPool;
import engine.exchange.MessageSystem;
import engine.main.GameSettings;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public class VoxelRenderer implements Runnable {
    private final MessageSystem messageSystem;
    private final EngineTaskPool taskPool;
    private final Window window;

    private volatile boolean running = true;
    private volatile Thread executionThread;

    private final List<EngineTask> localTaskList = new ArrayList<>(64);

    // Grafik-Ressourcen
    private ShaderProgram shader;
    private TextureManager textureManager;
    private final List<ChunkMesh> activeMeshes = new ArrayList<>();

    private final org.joml.Matrix4f mvpMatrix = new org.joml.Matrix4f();
    private final org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f();
    private final org.joml.Matrix4f viewMatrix = new org.joml.Matrix4f();

    // Kameraposition (Startposition leicht erhöht, um auf die Chunks zu blicken)
    private final org.joml.Vector3f cameraPos = new org.joml.Vector3f(0.0f, 15.0f, 30.0f);
    private float cameraRotationX = 20.0f; // Blick leicht nach unten
    private float cameraRotationY = 0.0f;

    public VoxelRenderer(MessageSystem messageSystem, EngineTaskPool taskPool, Window window) {
        this.messageSystem = messageSystem;
        this.taskPool = taskPool;
        this.window = window;
    }

    @Override
    public void run() {
        this.executionThread = Thread.currentThread();

        try {
            // 1. OpenGL-Kontext an DIESEN Thread binden und initialisieren
            initOpenGL();

            // 2. Render-Schleife
            while (running) {
                // Aufgaben abholen (Maximal 10 pro Frame)
                messageSystem.getTasks(MessageSystem.ThreadType.RENDER, 10, localTaskList);

                for (int i = 0; i < localTaskList.size(); i++) {
                    EngineTask task = localTaskList.get(i);
                    handleRenderTask(task);
                    task.executeCallback();
                    taskPool.free(task);
                }
                localTaskList.clear();

                // Zeichnen
                renderScene();

                // Puffer austauschen (Bringt das gezeichnete Bild auf den Bildschirm)
                window.swapBuffers();

                // Optionale kurze Pause zur Stabilisierung der Framerate (oder VSync nutzen)
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    running = false;
                }
            }
        } catch (Throwable t) {
            // Das zwingt Java, OpenGL- oder Speicherfehler trotz Gradle sichtbar auszugeben!
            System.err.println("!!! CRASH IM RENDER-THREAD !!!");
            t.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void handleRenderTask(EngineTask task) {
        if (task.getData() instanceof EngineData.LoadChunkData chunk) {
            System.out.println("[VOXEL-RENDERER] Generiere OpenGL-Mesh für Chunk bei X: " + chunk.chunkX());
            ChunkMesh newMesh = buildChunkMesh(chunk.chunkX(), chunk.chunkZ(), chunk.blockData());
            activeMeshes.add(newMesh);
        }
        else if (task.getData() instanceof EngineData.DeleteBufferData buffer) {
            glDeleteBuffers(buffer.bufferId());
        } else if (task.getData() instanceof EngineData.MoveCameraData move) {
            cameraPos.x += move.moveX();
            cameraPos.z += move.moveZ();
        }
    }

    private ChunkMesh buildChunkMesh(int cx, int cz, byte[][][] blockData) {
        java.util.List<Float> vertexList = new java.util.ArrayList<>();
        java.util.List<Float> normalList = new java.util.ArrayList<>();

        // Berechne den Welt-Offset für diesen Chunk
        float offsetX = cx * 16;
        float offsetZ = cz * 16;

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (blockData[x][y][z] == 0) continue;

                    // Absolute Positionen im Raum berechnen
                    float wx = x + offsetX;
                    float wy = y;
                    float wz = z + offsetZ;

                    if (z == 0  || blockData[x][y][z - 1] == 0) addFace(vertexList, normalList, wx, wy, wz, 0);
                    if (z == 15 || blockData[x][y][z + 1] == 0) addFace(vertexList, normalList, wx, wy, wz, 1);
                    if (x == 0  || blockData[x - 1][y][z] == 0) addFace(vertexList, normalList, wx, wy, wz, 2);
                    if (x == 15 || blockData[x + 1][y][z] == 0) addFace(vertexList, normalList, wx, wy, wz, 3);
                    if (y == 0  || blockData[x][y - 1][z] == 0) addFace(vertexList, normalList, wx, wy, wz, 4);
                    if (y == 15 || blockData[x][y + 1][z] == 0) addFace(vertexList, normalList, wx, wy, wz, 5);
                }
            }
        }

        float[] vertices = new float[vertexList.size()];
        for (int i = 0; i < vertexList.size(); i++) vertices[i] = vertexList.get(i);

        float[] normals = new float[normalList.size()];
        for (int i = 0; i < normalList.size(); i++) normals[i] = normalList.get(i);

        int vaoId = org.lwjgl.opengl.GL30.glGenVertexArrays();
        org.lwjgl.opengl.GL30.glBindVertexArray(vaoId);

        // 1. VBO für Positionen (Location = 0)
        int vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(0);

        // 2. VBO für Normalen (Location = 1)
        int nVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, nVboId);
        glBufferData(GL_ARRAY_BUFFER, normals, GL_STATIC_DRAW);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        org.lwjgl.opengl.GL30.glBindVertexArray(0);

        return new ChunkMesh(vaoId, vboId, vertices.length / 3);
    }

    private void addFace(java.util.List<Float> vertices, List<Float> normals, float x, float y, float z, int side) {
        switch (side) {
            case 0 -> { // HINTEN (Normale zeigt nach Z-)
                vertices.addAll(java.util.List.of(x+1, y, z, x, y, z, x, y+1, z, x+1, y, z, x, y+1, z, x+1, y+1, z));
                for(int i=0; i<6; i++) normals.addAll(java.util.List.of(0f, 0f, -1f));
            }
            case 1 -> { // VORNE (Normale zeigt nach Z+)
                vertices.addAll(java.util.List.of(x, y, z+1, x+1, y, z+1, x+1, y+1, z+1, x, y, z+1, x+1, y+1, z+1, x, y+1, z+1));
                for(int i=0; i<6; i++) normals.addAll(java.util.List.of(0f, 0f, 1f));
            }
            case 2 -> { // LINKS (Normale zeigt nach X-)
                vertices.addAll(java.util.List.of(x, y, z, x, y, z+1, x, y+1, z+1, x, y, z, x, y+1, z+1, x, y+1, z));
                for(int i=0; i<6; i++) normals.addAll(java.util.List.of(-1f, 0f, 0f));
            }
            case 3 -> { // RECHTS (Normale zeigt nach X+)
                vertices.addAll(java.util.List.of(x+1, y, z+1, x+1, y, z, x+1, y+1, z, x+1, y+1, z, x+1, y+1, z+1, x+1, y, z+1));
                for(int i=0; i<6; i++) normals.addAll(java.util.List.of(1f, 0f, 0f));
            }
            case 4 -> { // UNTEN (Normale zeigt nach Y-)
                vertices.addAll(java.util.List.of(x, y, z, x+1, y, z, x+1, y, z+1, x, y, z, x+1, y, z+1, x, y, z+1));
                for(int i=0; i<6; i++) normals.addAll(java.util.List.of(0f, -1f, 0f));
            }
            case 5 -> { // OBEN (Normale zeigt nach Y+)
                vertices.addAll(java.util.List.of(x, y+1, z+1, x+1, y+1, z+1, x+1, y+1, z, x, y+1, z+1, x+1, y+1, z, x, y+1, z));
                for(int i=0; i<6; i++) normals.addAll(java.util.List.of(0f, 1f, 0f));
            }
        }
    }

    private void renderScene() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (shader != null) {
            shader.bind();

            // 1. Projektions-Matrix berechnen (90° FOV, passendes Seitenverhältnis, 3D Clipping-Planes)
            float aspectRatio = (float) GameSettings.windowWidth / GameSettings.windowHeight;
            projectionMatrix.setPerspective((float) Math.toRadians(70.0f), aspectRatio, 0.1f, 1000.0f);

            // 2. View-Matrix (Kamera) berechnen
            viewMatrix.identity()
                    .rotateX((float) Math.toRadians(cameraRotationX))
                    .rotateY((float) Math.toRadians(cameraRotationY))
                    .translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            // 3. Kombinieren zu MVP (Model ist hier Identity, da Chunks bereits verschoben sind)
            mvpMatrix.set(projectionMatrix).mul(viewMatrix);

            // 4. An die Uniform-Variable im Shader übergeben
            shader.setUniform("mvpMatrix", mvpMatrix);

            // Chunks zeichnen
            for (ChunkMesh mesh : activeMeshes) {
                org.lwjgl.opengl.GL30.glBindVertexArray(mesh.getVaoId());
                glDrawArrays(GL_TRIANGLES, 0, mesh.getVertexCount());
            }

            org.lwjgl.opengl.GL30.glBindVertexArray(0);
            shader.unbind();
        }
    }

    private void initOpenGL() {
        System.out.println("[VOXEL-RENDERER] Aktiviere OpenGL-Kontext im Render-Thread...");

        // Binde den Kontext, der auf dem Main-Thread erstellt wurde, an diesen Thread
        glfwMakeContextCurrent(window.getHandle());

        // Erlaubt LWJGL die Interaktion mit den OpenGL-Treibern der GPU
        GL.createCapabilities();

        // Macht das Fenster erst jetzt sichtbar, damit es nicht weiß flackert
        glfwShowWindow(window.getHandle());

        // Hintergrundfarbe setzen (z.B. Himmelblau)
        glClearColor(0.4f, 0.6f, 0.9f, 1.0f);
        glEnable(GL_DEPTH_TEST); // Wichtig für 3D!

        textureManager = new TextureManager();

        String vShader = """
    #version 330 core
    layout (location = 0) in vec3 position;
    layout (location = 1) in vec3 normal; // Neu: Für die Beleuchtung
    
    out vec3 vNormal;
    
    uniform mat4 mvpMatrix;
    
    void main() {
        gl_Position = mvpMatrix * vec4(position, 1.0);
        vNormal = normal;
    }
    """;

        String fShader = """
    #version 330 core
    in vec3 vNormal;
    out vec4 fragColor;
    
    void main() {
        // Einfaches diffuses Licht von schräg oben
        vec3 lightDir = normalize(vec3(0.3, 1.0, 0.5));
        float diffuse = max(dot(vNormal, lightDir), 0.0);
        
        // Grundfarbe (z.B. Voxel-Grün) + Licht-Intensität
        vec3 blockColor = vec3(0.2, 0.7, 0.2);
        vec3 finalColor = blockColor * (diffuse * 0.6 + 0.4); // 40% Umgebungslicht
        
        fragColor = vec4(finalColor, 1.0);
    }
    """;
        shader = new ShaderProgram(vShader, fShader);

        System.out.println("[VOXEL-RENDERER] OpenGL erfolgreich initialisiert.");
    }

    private void cleanup() {
        System.out.println("[VOXEL-RENDERER] Starte OpenGL-Cleanup...");
        if (shader != null) shader.cleanup();
        if (textureManager != null) textureManager.cleanup();
        for (ChunkMesh mesh : activeMeshes) {
            mesh.cleanup();
        }
        activeMeshes.clear();
    }

    public void shutdown() {
        this.running = false;
        if (executionThread != null) {
            executionThread.interrupt();
        }
    }
}
