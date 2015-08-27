package com.saraga.cakra.proxymusicplayer.utils;

import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.ParseException;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.DefaultClientConnection;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.impl.conn.DefaultResponseParser;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by akhil on 25/8/15.
 * This class acts as a thread that handles the requests
 */
public class ProxyThread implements Runnable {

    Socket mSocket;
    ServerSocket mServerSocket;


    @Override
    public void run() {
        int port = 8080;

        DataOutputStream out = null;
        BufferedReader in = null;
        try {
            mServerSocket = new ServerSocket(port);
            mSocket = mServerSocket.accept();

            out = new DataOutputStream(mSocket.getOutputStream());
            in = new BufferedReader(
                    new InputStreamReader(mSocket.getInputStream()));

            String url = "http://stream.timesmusic.com/preview/mp3/1779.mp3";
            HttpResponse realResponse = download(url);

            InputStream data = realResponse.getEntity().getContent();
            StatusLine line = realResponse.getStatusLine();
            HttpResponse response = new BasicHttpResponse(line);
            response.setHeaders(realResponse.getAllHeaders());

            Log.d("Akhil", "reading headers");
            StringBuilder httpString = new StringBuilder();
            httpString.append(response.getStatusLine().toString());

            httpString.append("\n");
            for (Header h : response.getAllHeaders()) {
                httpString.append(h.getName()).append(": ").append(h.getValue()).append(
                        "\n");
            }
            httpString.append("\n");
            Log.d("Akhil", "headers done");

            try {

                File cacheDir=new File(android.os.Environment.getExternalStorageDirectory(),"Music_Cakra");
                if(!cacheDir.exists())
                    cacheDir.mkdirs();

                File f=new File(cacheDir,"1779.mp3");

                byte[] buffer = httpString.toString().getBytes();
                int readBytes;
                Log.d("Akhil", "writing to client");
                out.write(buffer, 0, buffer.length);


                OutputStream output = null;
                try {
                    output = new FileOutputStream(f);

                    // Start streaming content.
                    byte[] buff = new byte[1024 * 64];
                    while ((readBytes = data.read(buff, 0, buff.length)) != -1) {
                        out.write(buff, 0, readBytes);
                        output.write(buff, 0, readBytes);
                        Log.d("Akhil", readBytes+" bytes written");
                    }

                    Log.d("Akhil", "Completed writing");
                } catch (IOException e) {
                    Log.e("Akhil", e.getMessage(), e);
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
            } catch (Exception e) {
                Log.e("Akhil", e.getMessage(), e);
            } finally {
                if (data != null) {
                    data.close();
                }
                mSocket.close();
            }

        } catch (IOException e) {
            Log.e("Akhil", e.getMessage(), e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    Log.e("Akhil", e.getMessage(), e);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e("Akhil", e.getMessage(), e);
                }
            }
        }
    }

    private HttpResponse download(String url) {
        DefaultHttpClient seed = new DefaultHttpClient();
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        SingleClientConnManager mgr = new MyClientConnManager(seed.getParams(),
                registry);
        DefaultHttpClient http = new DefaultHttpClient(mgr, seed.getParams());
        HttpGet method = new HttpGet(url);
        HttpResponse response = null;
        try {
            Log.d("Akhil", "starting download");
            response = http.execute(method);
            Log.d("Akjhil", "downloaded");
        } catch (ClientProtocolException e) {
            Log.e("Akhil", "Error downloading", e);
        } catch (IOException e) {
            Log.e("Akhil", "Error downloading io ", e);
        }
        return response;
    }

    class MyClientConnManager extends SingleClientConnManager {
        private MyClientConnManager(HttpParams params, SchemeRegistry schreg) {
            super(params, schreg);
        }

        @Override
        protected ClientConnectionOperator createConnectionOperator(
                final SchemeRegistry sr) {
            return new MyClientConnectionOperator(sr);
        }

        class MyClientConnection extends DefaultClientConnection {
            @Override
            protected HttpMessageParser createResponseParser(
                    final SessionInputBuffer buffer,
                    final HttpResponseFactory responseFactory, final HttpParams params) {
                return new DefaultResponseParser(buffer, new IcyLineParser(),
                        responseFactory, params);
            }
        }

        class MyClientConnectionOperator extends DefaultClientConnectionOperator {
            public MyClientConnectionOperator(final SchemeRegistry sr) {
                super(sr);
            }

            @Override
            public OperatedClientConnection createConnection() {
                return new MyClientConnection();
            }
        }

        private class IcyLineParser extends BasicLineParser {
            private static final String ICY_PROTOCOL_NAME = "ICY";

            private IcyLineParser() {
                super();
            }

            @Override
            public boolean hasProtocolVersion(CharArrayBuffer buffer,
                                              ParserCursor cursor) {
                boolean superFound = super.hasProtocolVersion(buffer, cursor);
                if (superFound) {
                    return true;
                }
                int index = cursor.getPos();

                final int protolength = ICY_PROTOCOL_NAME.length();

                if (buffer.length() < protolength)
                    return false; // not long enough for "HTTP/1.1"

                if (index < 0) {
                    // end of line, no tolerance for trailing whitespace
                    // this works only for single-digit major and minor version
                    index = buffer.length() - protolength;
                } else if (index == 0) {
                    // beginning of line, tolerate leading whitespace
                    while ((index < buffer.length()) &&
                            HTTP.isWhitespace(buffer.charAt(index))) {
                        index++;
                    }
                } // else within line, don't tolerate whitespace

                return index + protolength <= buffer.length() &&
                        buffer.substring(index, index + protolength).equals(ICY_PROTOCOL_NAME);

            }


            @Override
            public ProtocolVersion parseProtocolVersion(CharArrayBuffer buffer,
                                                        ParserCursor cursor) throws ParseException {

                if (buffer == null) {
                    throw new IllegalArgumentException("Char array buffer may not be null");
                }
                if (cursor == null) {
                    throw new IllegalArgumentException("Parser cursor may not be null");
                }

                final int protolength = ICY_PROTOCOL_NAME.length();

                int indexFrom = cursor.getPos();
                int indexTo = cursor.getUpperBound();

                skipWhitespace(buffer, cursor);

                int i = cursor.getPos();

                // long enough for "HTTP/1.1"?
                if (i + protolength + 4 > indexTo) {
                    throw new ParseException
                            ("Not a valid protocol version: " +
                                    buffer.substring(indexFrom, indexTo));
                }

                // check the protocol name and slash
                if (!buffer.substring(i, i + protolength).equals(ICY_PROTOCOL_NAME)) {
                    return super.parseProtocolVersion(buffer, cursor);
                }

                cursor.updatePos(i + protolength);

                return createProtocolVersion(1, 0);
            }

            @Override
            public StatusLine parseStatusLine(CharArrayBuffer buffer,
                                              ParserCursor cursor) throws ParseException {
                return super.parseStatusLine(buffer, cursor);
            }
        }
    }
}