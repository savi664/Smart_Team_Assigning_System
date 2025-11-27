package Utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "app.log";

    private static BufferedWriter fileWriter;
    private static boolean isInitialized = false;

    public static void initialize() {
        try {
            // Create logs directory
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdir();
            }

            File logFile = new File(logDir, LOG_FILE);
            fileWriter = new BufferedWriter(new FileWriter(logFile, true)); // append = true
            isInitialized = true;

            log("INFO", "========== TEAM BUILDER APPLICATION STARTED ==========");
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static void log(String level, String message) {
        if (!isInitialized) return;

        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String logMessage = "[" + timestamp + "] [" + level + "] " + message;

            fileWriter.write(logMessage);
            fileWriter.newLine();
            fileWriter.flush();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public static void info(String message) { log("INFO", message); }
    public static void warning(String message) { log("WARNING", message); }
    public static void error(String message) { log("ERROR", message); }
    public static void debug(String message) { log("DEBUG", message); }

    public static void close() {
        try {
            if (fileWriter != null) {
                log("INFO", "========== TEAM BUILDER APPLICATION CLOSED ==========");
                fileWriter.close();
                isInitialized = false;
            }
        } catch (IOException e) {
            System.err.println("Error closing log file: " + e.getMessage());
        }
    }
}
