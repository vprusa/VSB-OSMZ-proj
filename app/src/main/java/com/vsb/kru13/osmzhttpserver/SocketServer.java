package com.vsb.kru13.osmzhttpserver;

import android.content.res.AssetManager;
import android.util.Log;

import com.vsb.kru13.osmzhttpserver.http.HttpRequest;
import com.vsb.kru13.osmzhttpserver.http.HttpResponse;
import com.vsb.kru13.osmzhttpserver.http.SimpleHttpParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

public class SocketServer extends Thread {

    private static String HTTP_NOT_FOUND_LABEL = "Not Found";
    private static String HTTP_VERISON_1_0 = "HTTP/1.0";

    private static HttpResponse HTTP_404;

    static {
        HTTP_404 = new HttpResponse(404, HTTP_NOT_FOUND_LABEL);
        HTTP_404.setVersion(HTTP_VERISON_1_0);
        HTTP_404.setBody(HTTP_NOT_FOUND_LABEL);
    }

    private static String WEB_DIR = "web";
    private static String DEFAULT_PAGE = "index.html";
    private final File sdCardDir;

    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;

    // TODO lombok, DI
    public SocketServer(AssetManager assets, File sdCardDir) {
        this.sdCardDir = sdCardDir;
    }

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.d("SERVER", "Error, probably interrupted in accept(), see log");
            e.printStackTrace();
        }
        bRunning = false;
    }

    public void run() {
        try {
            Log.d("SERVER", "Creating Socket");
            serverSocket = new ServerSocket(port);
            bRunning = true;

            while (bRunning) {
                Log.d("SERVER", "Socket Waiting for connection");
                Socket s = serverSocket.accept();
                Log.d("SERVER", "Socket Accepted");

                OutputStream o = s.getOutputStream();
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));

                HttpRequest request = SimpleHttpParser.parseRequest(s.getInputStream());
                String file = request.getUri();
                writeFile(out, file);

                out.flush();

                s.close();
                Log.d("SERVER", "Socket Closed");
            }
        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error");
                e.printStackTrace();
            }
        } finally {
            serverSocket = null;
            bRunning = false;
        }
    }

    private void writeFile(BufferedWriter bufferedWriter, String page) throws IOException {
        final String filePath;
        // simple URI router
        if (page != null && !page.isEmpty() && !page.equalsIgnoreCase("/")) {
            // is page a filename?
             if (page.matches(".*\\..*")) { // ^[\w,\s-]+\.[A-Za-z]{2,}$
//            if (page.matches("^[\\w,\\s-]+\\.[A-Za-z]{2,}$")) {
                filePath = WEB_DIR + "/" + page;
            } else {
                filePath = WEB_DIR + "/" + page + ".html";
            }
        } else {
            filePath = WEB_DIR + "/" + DEFAULT_PAGE;
        }
        final File fileOnSdCard = new File(sdCardDir, filePath);

        if (fileOnSdCard.exists()) {
            try (final InputStream inputStream = new FileInputStream(fileOnSdCard)) {
                final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                final HttpResponse response = createResponse(bufferedReader);
                bufferedWriter.write(response.toString());
            } catch (FileNotFoundException e) {
                Log.d("FileNotFoundException", e.toString());
            } catch (IOException e) {
                Log.d("IOException", e.toString());
            }

        } else {
            bufferedWriter.write(HTTP_404.toString()); // TODO send response 404
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
        response.addHeader("Content-Type", "text/plain");
        response.addHeader("Content-Length", "" + body.length());
        response.setBody(body);
        return response;
    }
}
