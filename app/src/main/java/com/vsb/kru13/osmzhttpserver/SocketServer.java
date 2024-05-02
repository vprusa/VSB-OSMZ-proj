package com.vsb.kru13.osmzhttpserver;

import android.util.Log;

import com.vsb.kru13.osmzhttpserver.http.HttpRequest;
import com.vsb.kru13.osmzhttpserver.http.HttpResponse;
import com.vsb.kru13.osmzhttpserver.http.SimpleHttpParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SocketServer extends Thread {
    private ServerSocket serverSocket;
    public final int port = 12345;
    private boolean bRunning;
    private final File sdCardDir;
    private Semaphore semaphore;
    private ExecutorService threadPool;

    private static String WEB_DIR = "web";
    private static String DEFAULT_PAGE = "index.html";

    private static String HTTP_NOT_FOUND_LABEL = "Not Found";

    private static int MAX_THREADS = 2;

    private static String ACCESS_LOG_FILE_PATH = "access.log";

    private static String ERRORS_LOG_FILE_PATH = "errors.log";

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * https://www.rfc-editor.org/rfc/rfc7231#section-6.5
     */
    private static HttpResponse HTTP_RESP_404 = new HttpResponse(
            404, HTTP_NOT_FOUND_LABEL, HTTP_NOT_FOUND_LABEL
    );

    /**
     * https://en.wikipedia.org/wiki/Hyper_Text_Coffee_Pot_Control_Protocol
     */
    private static HttpResponse HTTP_RESP_418 = new HttpResponse(
            418, "418", "I'm a teapot"
    );

    private static HttpResponse HTTP_RESP_503 = new HttpResponse(
            503, "Service Unavailable",
            "<html><body><h1>503 Service Unavailable</h1>" +
                    "<p>Server too busy. Please try again later.</p></body></html>",
            Collections.singletonMap("Content-Type", "text/html; charset=UTF-8")
    );

    public SocketServer(File sdCardDir) {
        this(sdCardDir, MAX_THREADS);
    }
    public SocketServer(File sdCardDir, int maxThreads) {
        this.sdCardDir = sdCardDir;
        this.semaphore = new Semaphore(maxThreads);
        this.threadPool = Executors.newFixedThreadPool(maxThreads);

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

    public void close() {
        try {
            bRunning = false;
            serverSocket.close();
            threadPool.shutdownNow();
        } catch (IOException e) {
            Log.e("SERVER", "Error while closing server", e);
        }
    }

    private void logServerError(Exception e) {
        Log.e("SERVER", "Server error", e);
    }

    private void appLog(String filePath, String tag, Socket s, String msg) {
        final File f = new File(sdCardDir, filePath);
        if (f.exists()) {
            String address = s != null ? s.getInetAddress().getHostAddress() : "unknown";
            String now = LocalDateTime.now().format(formatter);
            String log = String.format("%s - %s - address: %s, msg: %s\n\r", now, tag, address, msg);
            Log.e(tag, log);
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

    private void logAccess(Socket s, String msg) {
        appLog(ACCESS_LOG_FILE_PATH, "ACCESS_LOG", s, msg);
    }

    private void logError(Socket s, String msg) {
        appLog(ERRORS_LOG_FILE_PATH, "ERROR_LOG", s, msg);
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            bRunning = true;
            while (bRunning) {
                Socket client = serverSocket.accept();
                if (semaphore.tryAcquire()) {
                    logAccess(client, "acquired");
                    threadPool.execute(() -> {
                        try {
                            handleClient(client);
                        } catch (IOException e) {
                            logServerError(e);
                            logError(client, "server error");
                        } finally {
                            semaphore.release();
                            logAccess(client, "released");
                        }
                    });
                } else {
                    sendServerBusy(client);
                }
            }
        } catch (IOException e) {
            logServerError(e);
            logError(null, "server error");
        }
    }

    public void handleClient(Socket client) throws IOException {
        logAccess(client, "Socket Accepted");
        Log.d("SERVER", "Socket Accepted");

        OutputStream o = client.getOutputStream();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));

        HttpRequest request = SimpleHttpParser.parseRequest(client.getInputStream());
        String file = request.getUri();
        writeFile(out, file, o);

        out.flush();

        // client.close(); // TODO reevaluate closing the socket
        Log.d("SERVER", "Socket Closed");
        logAccess(client, "Socket Closed");
    }

    private void sendServerBusy(Socket client) {
        try (OutputStream out = client.getOutputStream()) {
            out.write(HTTP_RESP_503.toString().getBytes());
            out.flush();
        } catch (IOException e) {
            Log.e("SERVER", "Error sending 503 Service Unavailable", e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                Log.e("SERVER", "Error closing client socket during busy response", e);
            }
        }
    }

    private void writeFile(BufferedWriter bufferedWriter, String page, OutputStream o) throws IOException {
        final String filePath;
        // simple URI router
        if (page != null && !page.isEmpty() && !page.equalsIgnoreCase("/")) {
            // is page a filename?
            if (page.matches(".*\\..*")) { // ^[\w,\s-]+\.[A-Za-z]{2,}$
                // if (page.matches("^[\\w,\\s-]+\\.[A-Za-z]{2,}$")) {
                filePath = WEB_DIR + "/" + page;
            } else {
                filePath = WEB_DIR + "/" + page + ".html";
            }
        } else {
            filePath = WEB_DIR + "/" + DEFAULT_PAGE;
        }
        final File fileOnSdCard = new File(sdCardDir, URLDecoder.decode(filePath));

        if (fileOnSdCard.exists()) {
            String result = null;
            try (final InputStream inputStream = new FileInputStream(fileOnSdCard)) {
                final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                final HttpResponse response = createResponse(bufferedReader);
                if (page.matches(".*\\.png") || page.matches(".*\\.jpg")) {
                    result = null;
                    byte[] fileContent = loadFileContents(fileOnSdCard.getPath());

//                    o.write("HTTP/1.1 200 OK\r\n".getBytes());
                    o.write(response.getVersion().getBytes());
                    o.write("HTTP/1.1 200 OK\r\n".getBytes());
                    if (page.matches(".*\\.png")) {
                        o.write("Content-Type: image/png\r\n".getBytes());
                    } else if (page.matches(".*\\.jpg")) {
                        response.addHeader("Content-Type", "image/jpg");
                    }
                    o.write(("Content-Length: " + fileContent.length + "\r\n").getBytes());
                    o.write("\r\n".getBytes()); // End of headers
                    o.write(fileContent);
                    o.flush();
                    bufferedWriter.flush();
                } else {
                    response.addHeader("Content-Type", "text/plain");
                    result = response.toString();
                }
            } catch (FileNotFoundException e) {
                Log.d("FileNotFoundException", e.toString());
            } catch (IOException e) {
                Log.d("IOException", e.toString());
            }
            if (result != null) {
                bufferedWriter.write(result);
            } else {
                bufferedWriter.write(HTTP_RESP_418.toString()); // TODO
            }
        } else {
            bufferedWriter.write(HTTP_RESP_404.toString());
        }
        bufferedWriter.flush(); // Ensure all data is written out.
    }


    public HttpResponse createResponse(BufferedReader bufferedReader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
            // bufferedWriter.newLine();
        }
        return createResponse(stringBuilder.toString());
    }
    public HttpResponse createResponse(String body) {
        HttpResponse response = new HttpResponse(200, "OK");
        response.addHeader("Content-Length", "" + body.length());
        response.setBody(body);
        return response;
    }

    private byte[] loadFileContents(String filePath) throws IOException {
        FileInputStream fis = new FileInputStream(filePath);
        byte[] data = new byte[fis.available()];
        fis.read(data);
        fis.close();
        return data;
    }

}
