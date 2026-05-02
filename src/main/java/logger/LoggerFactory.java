package logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LoggerFactory {
    static PrintWriter writer;
    static String timeStamp;
    static String dirPath;
    static String fileName;

    public static void init() {
        try {

            timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            dirPath = "src/main/logs/";
            fileName = dirPath + "log_for_" + timeStamp + ".txt";

            Files.createDirectories(Paths.get(dirPath));
            writer = new PrintWriter(fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    public static Logger getLogger(String name) {
        String threadName = Thread.currentThread().getName();
        return new Logger(threadName, name);
    }
}
