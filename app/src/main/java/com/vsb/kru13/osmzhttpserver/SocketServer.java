package com.vsb.kru13.osmzhttpserver;

import android.content.res.AssetManager;
import android.util.Log;

import com.vsb.kru13.osmzhttpserver.http.HttpRequest;
import com.vsb.kru13.osmzhttpserver.http.SimpleHttpParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer extends Thread {

    private final AssetManager assets;
    private static String WEB_DIR = "web";
    private static String DEFAULT_PAGE = "index.html";

    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;

    // TODO DI
    public SocketServer(AssetManager assets) {
        this.assets = assets;
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
        }
        catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error");
                e.printStackTrace();
            }
        }
        finally {
            serverSocket = null;
            bRunning = false;
        }
    }

    private void writeFile(BufferedWriter bufferedWriter, String page) throws IOException {
        final String file;
        if (page == null) {
            file = WEB_DIR + "/" + page + ".html";
        } else {
            file = WEB_DIR + "/index.html";
        }
        InputStream inputStream = assets.open(file);
        /*
        int size = inputStream.available();
        byte[] buffer = new byte[size]; //declare the size of the byte array with size of the file
        inputStream.read(buffer); //read file
        inputStream.close(); //close file
        */
//        out.write(buffer);

        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            bufferedWriter.write(line);
            bufferedWriter.newLine(); // Add a new line for each line read.
        }
        bufferedWriter.flush(); // Ensure all data is written out.
// Store text file data in the string variable
//        String str_data = new String(buffer);
    }

    private String resolvePage(HttpRequest request) {


        return "TODO";
    }

}

