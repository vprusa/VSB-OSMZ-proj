package com.vsb.kru13.osmzhttpserver;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AppLogger {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String ACCESS_LOG_FILE_PATH = "access.log";

    public static final String ERRORS_LOG_FILE_PATH = "errors.log";

    private final File sdCardDir;

    public AppLogger(File sdCardDir) {
        AppLogger.initLogFiles(sdCardDir);
        this.sdCardDir = sdCardDir;
    }

    private static void initLogFiles(File sdCardDir) {
        final File accessFile = new File(sdCardDir, ACCESS_LOG_FILE_PATH);
        final File errorsFile = new File(sdCardDir, ERRORS_LOG_FILE_PATH);
        if (!accessFile.exists()) {
            try {
                accessFile.createNewFile();
            } catch (IOException e) {
                Log.e("FILE", "Unable to create access log files on SD card", e);
            }
        }
        if (!errorsFile.exists()) {
            try {
                errorsFile.createNewFile();
            } catch (IOException e) {
                Log.e("FILE", "Unable to create error log files on SD card", e);
            }
        }
    }

    private void appLog(String filePath, String tag, Socket s, String msg) {
        final File f = new File(sdCardDir, filePath);
        if (f.exists()) {
            final String address = s != null ? s.getInetAddress().getHostAddress() : "unknown";
            final String now = LocalDateTime.now().format(formatter);
            final String log = String.format("%s - %s - address: %s, msg: %s\n\r", now, tag, address, msg);
            Log.i(tag, log);
            if (f.exists() && f.canWrite()) {
                // TODO add semaphore?
                try (FileWriter writer = new FileWriter(f)) {
                    writer.write(log);
                    writer.flush();
                } catch (IOException e) {
                    Log.e("FILE_WRITE", "Writing access log error", e);
                }
            }
        }
    }

    public void logAccess(Socket s, String msg) {
        appLog(ACCESS_LOG_FILE_PATH, "ACCESS_LOG", s, msg);
    }

    public void logAccess(String tag, String msg) {
        appLog(ACCESS_LOG_FILE_PATH, "ACCESS_LOG", null, msg);
    }

    public void logError(Socket s, String msg) {
        appLog(ERRORS_LOG_FILE_PATH, "ERROR_LOG", s, msg);
    }

    public void logError(String tag, String msg) {
        appLog(ERRORS_LOG_FILE_PATH, tag, null, msg);
    }
}
