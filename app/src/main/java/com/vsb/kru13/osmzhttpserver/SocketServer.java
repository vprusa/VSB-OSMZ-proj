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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

public class SocketServer extends Thread {

    private static String HTTP_NOT_FOUND_LABEL = "Not Found";

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

    private static String WEB_DIR = "web";
    private static String DEFAULT_PAGE = "index.html";
    private final File sdCardDir;

    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;

    // TODO lombok, DI
    public SocketServer(File sdCardDir) {
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
                writeFile(out, file, o);

                out.flush();

//                s.close();
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

    public static byte[] loadFileContents(String filePath) throws IOException {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IOException("File does not exist: " + filePath);
        }

        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("File is too large to load into a byte array.");
        }

        byte[] buffer = new byte[(int) fileSize];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = 0;
            int bytesToRead = buffer.length;
            while (bytesRead < bytesToRead) {
                int result = fis.read(buffer, bytesRead, bytesToRead - bytesRead);
                if (result == -1) break; // End of file reached
                bytesRead += result;
            }

            if (bytesRead < bytesToRead) {
                throw new IOException("Unexpected end of file; was expecting " + bytesToRead + " bytes, but only received " + bytesRead);
            }

            return buffer;
        }
    }

    private void writeFile(BufferedWriter bufferedWriter, String page, OutputStream o) throws IOException {
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
        final File fileOnSdCard = new File(sdCardDir, URLDecoder.decode(filePath));

        if (fileOnSdCard.exists()) {
            String result = null;
            try (final InputStream inputStream = new FileInputStream(fileOnSdCard)) {
                final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                final HttpResponse response = createResponse(bufferedReader);
                if (page.matches(".*\\.png") || page.matches(".*\\.jpg")) {
                    result = null;
//                        response.addHeader("Content-Type", "image/png");
//                        result = response.toString().getBytes();
//                        o.write();
//                    byte[] fileContent = new byte[(int) fileOnSdCard.length()];
//                    inputStream.read(fileContent, 0, (int) fileOnSdCard.length());
                    byte[] fileData = loadFileContents(fileOnSdCard.getPath());
                    byte[] fileContent = fileData; //new byte[(int) fileOnSdCard.length()];
                    /*int offset = 0;
                    int numRead = 0;
                    while (offset < fileContent.length && (numRead=inputStream.read(fileContent, offset, fileContent.length - offset)) >= 0) {
                        offset += numRead;
                    }*/


                    o.write("HTTP/1.1 200 OK\r\n".getBytes());
                    if (page.matches(".*\\.png")) {
                        o.write("Content-Type: image/png\r\n".getBytes());
                    } else if (page.matches(".*\\.jpg")) {
                        response.addHeader("Content-Type", "image/jpg");
                    }
                    o.write(("Content-Length: " + fileContent.length + "\r\n").getBytes());
                    o.write("\r\n".getBytes()); // End of headers
                    //Log.d("fileContent", fileContent.toString());
                    o.write(fileContent);
                    o.flush();
                    bufferedWriter.flush();
//                    } else if (page.matches(".*\\.jpg")) {
//                        response.addHeader("Content-Type", "image/jpg");
//                        result = response.toString().getBytes();
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
}
