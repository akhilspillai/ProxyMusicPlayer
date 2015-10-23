package com.saraga.cakra.proxymusicplayer.utils;

import android.util.Log;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;

/**
 * Created by akhil on 25/8/15.
 * This class acts as a thread that handles the requests
 */
public class ProxyThread implements Runnable {

    Socket mSocket;
    ServerSocket mServerSocket;

    private static final String RESPONSE_LINE = "HTTP/1.1 200 OK",
            ACCEPT_RANGE = "Accept-Ranges: bytes",
            CONTENT_LENGTH = "Content-Length: ",
            KEEP_ALIVE = "Keep-Alive: timeout=5, max=100",
            CONNECTION = "Connection: Keep-Alive",
            CONTENT_TYPE = "Content-Type: audio/mpeg";


    @Override
    public void run() {
        int port = 8080;

        BufferedReader in = null;
        try {
            mServerSocket = new ServerSocket(port);
            mSocket = mServerSocket.accept();

            in = new BufferedReader(
                    new InputStreamReader(mSocket.getInputStream()));

            String url = in.readLine();
            int start = url.indexOf("url=") + 4;
            int end = url.indexOf(" HTTP/1.1");
            url = url.substring(start, end);


            File cacheDir = new File(android.os.Environment.getExternalStorageDirectory(), "Music_Cakra");
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            File f = new File(cacheDir, "1779.mp3");
            if (f.exists()) {
                playFromFile(f);
            } else {
                f = new File(cacheDir, "1779.part");
                playFromUrl(url, f);
            }


        } catch (Exception e) {
            Log.e("Akhil", e.getMessage(), e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void playFromFile(File f) throws IOException {
        InputStream stream = new FileInputStream(f);
        StringBuilder httpString = new StringBuilder();
        httpString.append("HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: ")
                .append(Long.toString(f.length()))
                .append("\r\nContent-Range: bytes ")
                .append(Long.toString(0))
                .append("-")
                .append(Long.toString(f.length() - 1))
                .append("/")
                .append(f.length())
                .append("\r\nContent-Type: application/octet-stream\r\n\r\n");

        Log.d("Akhil", httpString.toString());

        DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());
        byte[] buffer = httpString.toString().getBytes();
        int readBytes;
        Log.d("Akhil", "writing to client");
        out.write(buffer, 0, buffer.length);

        byte[] buff = new byte[1024 * 64];
        while ((readBytes = stream.read(buff, 0, buff.length)) != -1) {
            out.write(buff, 0, readBytes);
            Log.d("Akhil", readBytes + " bytes written");
        }
    }

    private void playFromUrl(String url, File f) throws IOException {
        OutputStream output = null;
        try {
            DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());
            StringBuilder httpString = new StringBuilder();
            Response okHttpResponse = getSongFromUrl(url);
            InputStream stream = okHttpResponse.body().byteStream();

            String statusLine = okHttpResponse.protocol()+" "+okHttpResponse.code()+" "+okHttpResponse.message();
            httpString.append(statusLine.toUpperCase());

            httpString.append("\r\n");
            Log.d("Akhil", "reading headers");

            Headers headers = okHttpResponse.headers();
            Set<String> headerNames = headers.names();
            for (String name : headerNames) {
                httpString.append(name).append(": ").append(headers.get(name)).append(
                        "\r\n");
            }
            httpString.append("\r\n");


            Log.d("Akhil", "httpString is " + httpString);

            byte[] buffer = httpString.toString().getBytes();
            int readBytes;
            Log.d("Akhil", "writing to client");
            out.write(buffer, 0, buffer.length);

            output = new FileOutputStream(f);

            // Start streaming content.
            byte[] buff = new byte[1024 * 64];
            while ((readBytes = stream.read(buff, 0, buff.length)) != -1) {
                out.write(buff, 0, readBytes);
                output.write(buff, 0, readBytes);
            }
            String filename = f.getAbsolutePath();
            filename = filename.substring(0, filename.length() - 4);
            f.renameTo(new File(filename + "mp3"));
            Log.d("Akhil", "Completed writing");
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    Log.e("Akhil", e.getMessage(), e);
                }
            }
        }
    }

    public void release() {
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
                mServerSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Response getSongFromUrl(String url) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        return client.newCall(request).execute();
    }

}