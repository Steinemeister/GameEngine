package engine.main;

import engine.exchange.*;
import engine.rendering.VoxelRenderer;
import engine.rendering.Window;
import engine.world.ChunkManager;

import java.util.ArrayList;
import java.util.List;

public class Engine {
    private final EngineTaskPool taskPool = new EngineTaskPool();
    private final MessageSystem messageSystem = new MessageSystem(taskPool);

    // Wiederverwendbare Liste für Antworten an den Main-Thread (GC-Frei)
    private final List<EngineTask> localMainTaskList = new ArrayList<>(32);

    // Welt-Verwaltung
    private final ChunkManager chunkManager = new ChunkManager();

    // Fenster-Instanz
    private Window window;

    // Die Runnables für die Kontext-Threads
    private VoxelRenderer voxelRenderer;

    private Thread renderThread;
    private volatile boolean running = false;

    private boolean worldGenerated = false;

    public static void main(String[] args) {
        Engine engine = new Engine();
        engine.start();
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        System.out.println("[ENGINE] Initialisiere Fenster auf dem Main-Thread...");

        try {
            // 1. Fenster auf dem Main-Thread erstellen (Zwingend erforderlich für GLFW!)
            window = new Window();
            window.create();

            // 2. Render-Runnable erstellen und das Fenster übergeben
            voxelRenderer = new VoxelRenderer(messageSystem, taskPool, window);

            // 3. Render-Thread starten
            System.out.println("[ENGINE] Starte Render-Thread...");
            renderThread = new Thread(voxelRenderer, "Render-Thread");
            renderThread.start();

            // 4. Den Haupt-Game-Loop in diesem Thread (Main-Thread) starten
            gameLoop();

        } catch (Throwable t) {
            System.err.println("!!! KRITISCHER FEHLER BEIM ENGINE-START !!!");
            t.printStackTrace();
        } finally {
            cleanUpAndShutdown();
        }
    }

    /**
     * Präziser, entkoppelter Game-Loop.
     */
    private void gameLoop() {
        final double timeBetweenTicks = 1.0 / GameSettings.TICKS_PER_SECOND;
        double nextTickTime = System.nanoTime() / 1_000_000_000.0;

        // Die Schleife bricht ab, wenn running=false ODER das Fenster geschlossen wird
        while (running && !window.shouldClose()) {
            double currentTime = System.nanoTime() / 1_000_000_000.0;

            // Logik-Ticks nachholen, falls der Thread hinterherhinkt
            while (currentTime >= nextTickTime) {
                update();
                nextTickTime += timeBetweenTicks;
            }

            // Zwischen den Ticks: Eingehende Thread-Antworten auswerten
            processThreadMessages();

            // WICHTIG: GLFW Event-Polling MUSS auf dem Main-Thread laufen!
            window.updateEvents();

            long handle = window.getHandle();
            float speed = 0.4f;
            float mx = 0, mz = 0;

            if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS) mz -= speed;
            if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == org.lwjgl.glfw.GLFW.GLFW_PRESS) mz += speed;
            if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS) mx -= speed;
            if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == org.lwjgl.glfw.GLFW.GLFW_PRESS) mx += speed;

            if (mx != 0 || mz != 0) {
                // Sende Bewegungsdaten mit hoher Priorität an den Render-Thread
                EngineTask moveTask = taskPool.obtain(TaskPriority.HIGH, new EngineData.MoveCameraData(mx, mz));
                messageSystem.sendMessage(MessageSystem.ThreadType.RENDER, moveTask);
            }

            // Dem OS eine kurze Atempause geben
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                running = false;
            }
        }
    }

    /**
     * Ticks: Hier läuft deine gesamte Spiellogik (20Hz).
     */
    private void update() {
        if (!worldGenerated) {
            worldGenerated = true;

            // Generiere ein 3x3 Chunk-Raster asynchron!
            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    triggerAsynchronousChunkGeneration(cx, cz);
                }
            }
        }
    }

    /**
     * Liest Antworten und Ergebnisse ein, die zurück an den Main-Thread gingen.
     */
    private void processThreadMessages() {
        messageSystem.getTasks(MessageSystem.ThreadType.MAIN, 10, localMainTaskList);

        for (int i = 0; i < localMainTaskList.size(); i++) {
            EngineTask task = localMainTaskList.get(i);

            if (task.getData() instanceof EngineData.LoadTextureResponse CollegeResponse) {
                System.out.println("[MAIN] Textur registriert.");
            }

            taskPool.free(task);
        }
        localMainTaskList.clear();
    }

    private void triggerAsynchronousChunkGeneration(int cx, int cz) {
        messageSystem.sendMessage(MessageSystem.ThreadType.ASYNC_WORKER, () -> {
            System.out.println("[ASYNC-WORKER] Generiere 16x16x16 Blöcke für Chunk " + cx + "/" + cz);

            byte[][][] blockData = new byte[16][16][16];

            // Generiere eine hügelige Landschaft im 16³ Raum
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // Absolute Weltkoordinaten berechnen
                    int worldX = cx * 16 + x;
                    int worldZ = cz * 16 + z;

                    // Erzeuge Wellenberge und -täler zwischen Höhe 2 und 12
                    int height = (int) (7 + Math.sin(worldX * 0.2) * 3 + Math.cos(worldZ * 0.2) * 2);
                    // Begrenzen auf Chunk-Höhe
                    height = Math.max(0, Math.min(15, height));

                    for (int y = 0; y < 16; y++) {
                        if (y < height) {
                            blockData[x][y][z] = 1; // Stein/Erde
                        } else if (y == height) {
                            blockData[x][y][z] = 2; // Grasblock
                        } else {
                            blockData[x][y][z] = 0; // Luft
                        }
                    }
                }
            }

            // Daten-Task an den VoxelRenderer übergeben
            EngineTask renderTask = taskPool.obtain(
                    TaskPriority.HIGH,
                    new EngineData.LoadChunkData(cx, cz, blockData)
            );
            messageSystem.sendMessage(MessageSystem.ThreadType.RENDER, renderTask);
        });
    }

    public synchronized void stop() {
        running = false;
    }

    private void cleanUpAndShutdown() {
        System.out.println("[ENGINE] Starte koordinierten Shutdown aller Systeme...");

        // 1. Sub-Threads stoppen
        if (voxelRenderer != null) voxelRenderer.shutdown();

        // 2. Den kontextlosen Async-Worker-Pool im MessageSystem abbrechen
        messageSystem.shutdown();

        try {
            // 3. Warten, bis der Render-Thread sein internes OpenGL-Cleanup beendet hat
            if (renderThread != null) renderThread.join(2000);
        } catch (InterruptedException e) {
            System.err.println("[ENGINE] Fehler beim Warten auf Render-Thread.");
        }

        // 4. Fenster auf dem Main-Thread zerstören (Gleicher Ort wie die Erstellung)
        if (window != null) {
            System.out.println("[ENGINE] Zerstöre Fenster und beende GLFW...");
            window.destroy();
        }
        System.out.println("[ENGINE] Engine erfolgreich geschlossen.");
    }
}
