package logger;

import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class Logger {
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final PrintWriter writer;
    private final String ThreadName;
    private final String name;

    //colorCodes
    private static final String BLUE = "\u001B[34m";
    public static final String RESET = "\u001B[0m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String CYAN = "\u001B[36m";

    //logEntry types
    private static final byte INFO = 0;
    private static final byte WARNING = 1;
    private static final byte ERROR = 2;

    public Logger(String threadName, String name) {
        this.writer = LoggerFactory.writer;
        this.ThreadName = threadName;
        this.name = name;
    }

    public void info(String info) {
        System.out.println(BLUE + log(INFO, info) + RESET);
    }

    public void warn(String warning) {
        System.out.println(YELLOW + log(WARNING, warning) + RESET);
    }

    public void error( Exception e) {
        System.out.println(RED + log(ERROR, "an unhandled error occurred: " + e.getMessage()) + RESET);
        e.printStackTrace(writer);
    }

    public void error(String errorMessage) {
        System.out.println(RED + log(ERROR, errorMessage + " stackTrace: \n"
                + Arrays.toString(new Error().getStackTrace())) + RESET);
    }

    private String log(byte type, String text) {
        String logEntry;
        String info = "[" + getTime() + "][" + ThreadName + "][" + name;
        switch (type) {
            case INFO -> logEntry = info + "/info]: " + text;
            case WARNING -> logEntry = info + "/warn]: " + text;
            case ERROR -> logEntry = info + "/error]: " + text;
            default -> logEntry = "log-entry type not found";
        }
        writer.println(logEntry);
        writer.flush();
        return logEntry;
    }

    private String getTime() {
        return LocalTime.now().format(format);
    }
}
