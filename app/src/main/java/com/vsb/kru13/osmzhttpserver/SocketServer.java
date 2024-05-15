package com.vsb.kru13.osmzhttpserver;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;


import com.vsb.kru13.osmzhttpserver.controllers.TelemetryCollector;
import com.vsb.kru13.osmzhttpserver.http.HttpRequest;
import com.vsb.kru13.osmzhttpserver.http.HttpResponse;
import com.vsb.kru13.osmzhttpserver.http.SimpleHttpParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SocketServer extends Thread {
    private final AppLogger logger;
    private final AssetManager assetManager;
    private ServerSocket serverSocket;
    public final int port = 12345;
    private boolean bRunning;
    private final File sdCardDir;
    private final Semaphore semaphore;
    private final ExecutorService threadPool;

    private Camera2CaptureSession camera2CaptureSession;

    private static final String WEB_DIR = "web";
    private static final String DEFAULT_PAGE = "index.html";

    private static final String HTTP_NOT_FOUND_LABEL = "Not Found";

    private static final int MAX_THREADS = 2;

    private static final String TAG_ERR = "SOCKET";

    /**
     * https://www.rfc-editor.org/rfc/rfc7231#section-6.5
     */
    private static final HttpResponse HTTP_RESP_404 = new HttpResponse(
            404, HTTP_NOT_FOUND_LABEL, HTTP_NOT_FOUND_LABEL
    );

    /**
     * https://en.wikipedia.org/wiki/Hyper_Text_Coffee_Pot_Control_Protocol
     */
    private static final HttpResponse HTTP_RESP_418 = new HttpResponse(
            418, "418", "I'm a teapot"
    );

    private static final HttpResponse HTTP_RESP_503 = new HttpResponse(
            503, "Service Unavailable",
            "<html><body><h1>503 Service Unavailable</h1>" +
                    "<p>Server too busy. Please try again later.</p></body></html>",
            Collections.singletonMap("Content-Type", "text/html; charset=UTF-8")
    );

    private final TelemetryCollector telemetryCollector;
    private Camera camera;
    private boolean streaming;
    private final Object streamLock = new Object();

    public SocketServer(Context context, File sdCardDir, TelemetryCollector telemetryCollector, AssetManager assetManager) {
        this(context, sdCardDir, MAX_THREADS, telemetryCollector, assetManager);
    }
    public SocketServer(Context context,
                        File sdCardDir,
                        int maxThreads,
                        TelemetryCollector telemetryCollector,
                        AssetManager assetManager

    ) {
        this.assetManager = assetManager;
        this.telemetryCollector = telemetryCollector;
        this.sdCardDir = sdCardDir;
        this.semaphore = new Semaphore(maxThreads);
        this.threadPool = Executors.newFixedThreadPool(maxThreads);
        logger = new AppLogger(sdCardDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.camera2CaptureSession = new Camera2CaptureSession(context, logger);
        }
    }

    public void close() {
        try {
            bRunning = false;
            serverSocket.close();
            threadPool.shutdownNow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera2CaptureSession.stopCameraStream();
            }
        } catch (IOException e) {
            Log.e("SERVER", "Error while closing server", e);
        }
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            bRunning = true;
            while (bRunning) {
                final Socket client = serverSocket.accept();
                if (semaphore.tryAcquire()) {
                    logger.logAccess(client, "acquired");
                    threadPool.execute(() -> {
                        try {
                            handleClient(client);
                        } catch (IOException | JSONException e) {
                            logger.logError(client, "server error");
                        } finally {
                            semaphore.release();
                            logger.logAccess(client, "released");
                        }
                    });
                } else {
                    sendServerBusy(client);
                }
            }
        } catch (IOException e) {
            logger.logError(TAG_ERR, "server error");
        }
    }

    /**
     * Handle Client connections.
     *
     * @param client
     * @throws IOException
     * @throws JSONException
     */
    public void handleClient(Socket client) throws IOException, JSONException {
        logger.logAccess(client, "Socket Accepted");
        Log.d("SERVER", "Socket Accepted");

        final OutputStream o = client.getOutputStream();
        final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));

        final HttpRequest request = SimpleHttpParser.parseRequest(client.getInputStream());
        final String uri = request.getUri();

        if (uri.equals("/streams/telemetry")) {
            if (telemetryCollector != null) {
                final JSONObject telemetryData = telemetryCollector.getTelemetryData();
                final HttpResponse response = new HttpResponse(
                        200,
                        "OK",
                        telemetryData.toString(),
                        Collections.singletonMap("Content-Type", "application/json")
                );
                out.write(response.toString());
                out.flush();
                out.close();
            }
        } else if (uri.equals("/camera/stream")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera2CaptureSession.startCameraStream(o);//client);
            }
        } else {
            writeFile(out, uri, o); // Existing file serving logic
        }
        out.flush();

        // client.close(); // TODO reevaluate closing the socket
        Log.d("SERVER", "Socket Closed");
        logger.logAccess(client, "Socket Closed");
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

    private void writeFile(BufferedWriter bufferedWriter, final String inPage, OutputStream o) throws IOException {
        final String filePath;
        // simple URI router
        boolean isCmd = false;
        final String resolvedPage;
        if (inPage != null && !inPage.isEmpty() && !inPage.equalsIgnoreCase("/")) {
            // is page a filename?
            // TODO finalize page for code readability
            if (inPage.startsWith("/")) {
                resolvedPage = inPage.replaceFirst("/", "");
                if (resolvedPage.startsWith("cmd/")) {
                    isCmd = true;
                }
            } else {
                resolvedPage = inPage;
            }
            filePath = WEB_DIR + "/" + resolvedPage;
        } else {
            resolvedPage = inPage;
            filePath = WEB_DIR + "/" + inPage;
        }

        if (isCmd) {
            handleCommandExecution(bufferedWriter, resolvedPage, o);
        } else {
            handleServingFile(bufferedWriter, resolvedPage, o, filePath);
        }
        bufferedWriter.flush(); // Ensure all data is written out.
        bufferedWriter.close();
    }

    private void handleCommandExecution(BufferedWriter bufferedWriter, String page, OutputStream o) throws IOException {
        // request is command and so I will write the whole logic here.. it will need to be refactored.
        String cmdWithArgs = page.replace("cmd/", "");
        String[] cmdWithArgsArr = cmdWithArgs.split("%20");
        // TODO #5

        ProcessBuilder processBuilder = new ProcessBuilder(cmdWithArgsArr);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            output.append("<html><body><pre>");
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            output.append("</pre></body></html>");

            HttpResponse response = createResponse(output.toString());
            o.write(response.toString().getBytes());
            o.flush();
            bufferedWriter.flush();
        } catch (IOException e) {
            Log.e("SERVER", "Error executing command", e);
            bufferedWriter.write(HTTP_RESP_404.toString());
        }
    }

    private void handleServingFile(BufferedWriter bufferedWriter, String page, OutputStream o, String filePath) throws IOException {
        final File fileOnSdCard = new File(sdCardDir, URLDecoder.decode(filePath));
        InputStream fileInAssets;
        try {
            fileInAssets = assetManager.open(URLDecoder.decode(filePath));
        } catch (FileNotFoundException e) {
            fileInAssets = null;
        }
        if (fileOnSdCard.exists() || fileInAssets != null) {
            handleServerFileOnSDCardOrInAssets(bufferedWriter, page, o, fileOnSdCard, filePath, fileInAssets);
        } else {
            bufferedWriter.write(HTTP_RESP_404.toString());
        }
    }

    private void handleServerFileOnSDCardOrInAssets(BufferedWriter bufferedWriter, String page, OutputStream o, File fileOnSdCard, String filePath, InputStream fileInAssets) {
        String result = null;
        try {
            if (fileOnSdCard.isDirectory()) {
                HttpResponse response = serveDirectory(fileOnSdCard, filePath);
                o.write(response.toString().getBytes());
                o.flush();
                bufferedWriter.flush();
            } else if (fileInAssets == null) {
                // in if below is the old solution that needs to be refactored
                // and integrated with #5
                if (page.matches(".*\\.png") || page.matches(".*\\.jpg")) {
                    byte[] fileContent = loadFileContents(fileOnSdCard.getPath());
                    o.write("HTTP/1.1 200 OK\r\n".getBytes());
                    if (page.matches(".*\\.png")) {
                        o.write("Content-Type: image/png\r\n".getBytes());
                    } else if (page.matches(".*\\.jpg")) {
                        o.write("Content-Type: image/jpg\r\n".getBytes());
                    }
                    o.write(("Content-Length: " + fileContent.length + "\r\n").getBytes());
                    o.write("\r\n".getBytes()); // End of headers
                    o.write(fileContent);
                    o.flush();
                    o.close();
                    bufferedWriter.flush();
                } else {
                    final HttpResponse response = new HttpResponse(200, "OK");
//                            response.addHeader("Content-Type", "text/plain");
                    result = response.toString();
                    bufferedWriter.write(result);
                }
                if (result != null) {
                    bufferedWriter.write(result);
                } else {
                    bufferedWriter.write(HTTP_RESP_418.toString()); // TODO
                }
            } else {
                final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInAssets));
                HttpResponse response = createResponse(bufferedReader);
                o.write(response.toString().getBytes());
                o.flush();
                bufferedWriter.flush();
            }

        } catch (FileNotFoundException e) {
            Log.d("FileNotFoundException", e.toString());
        } catch (IOException e) {
            Log.d("IOException", e.toString());
        }
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

    private HttpResponse serveDirectory(File directory, String uriPath) throws IOException {
        File indexHtml = new File(directory, "index.html");
        File indexHtm = new File(directory, "index.htm");
        final HttpResponse result;
        if (indexHtml.exists()) {
            result = serveFile(indexHtml);
        } else if (indexHtm.exists()) {
            result = serveFile(indexHtm);
        } else {
            File[] files = directory.listFiles();
            if (files != null) {
                StringBuilder html = new StringBuilder("<html><body>");
                html.append("<h1>Directory listing for ").append(directory.getName()).append("</h1>");
                html.append("<ul>");

                // add optional links to navigate in dir structure
                if (!uriPath.equals(WEB_DIR)) {
                    String parentPath = uriPath.substring(0, uriPath.lastIndexOf("/"));
                    html.append("<li><a href=\"").append(parentPath).append("\">..</a></li>");
                }

                // add links to each file..
                for (File file : files) {
                    if (file.isDirectory()) {
                        html.append("<li><a href=\"").append(uriPath).append("/").append(file.getName()).append("/\">")
                                .append(file.getName()).append("/</a></li>");
                    } else {
                        html.append("<li><a href=\"").append(uriPath).append("/").append(file.getName()).append("\">")
                                .append(file.getName()).append("</a></li>");
                    }
                }

                html.append("</ul>");
                html.append("</body></html>");

                final HttpResponse response = new HttpResponse(200, "OK");
//                response.addHeader("Content-Type", "text/plain");
                response.setBody(html.toString());
                result = response;
            } else {
                result = HTTP_RESP_404;
            }
        }
        return result;
    }

    private HttpResponse serveFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        final HttpResponse response = new HttpResponse(200, "OK");
//        response.addHeader("Content-Type", "text/plain");
        response.setBody(new String(data));
        return response;
    }

}


