package com.arzaroth.lumen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** A local web server answering each path with a fixed body, or 404; `hits` counts the requests. */
final class LocalWeb implements AutoCloseable {
    private final ServerSocket server;
    final String base;
    final ConcurrentHashMap<String, String> pages = new ConcurrentHashMap<>();
    volatile int hits;

    LocalWeb() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        base = "http://127.0.0.1:" + server.getLocalPort();
        Thread t = new Thread(this::serve, "local-web");
        t.setDaemon(true);
        t.start();
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                String path = in.readLine().split(" ")[1];
                for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                    continue;
                }
                hits++;
                int q = path.indexOf('?');
                String page = pages.get(q < 0 ? path : path.substring(0, q));
                byte[] body = page == null ? new byte[0] : page.getBytes(StandardCharsets.UTF_8);
                OutputStream out = s.getOutputStream();
                out.write(("HTTP/1.1 " + (page == null ? 404 : 200) + " X\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.write(body);
                out.flush();
            } catch (IOException | RuntimeException e) {
                if (server.isClosed()) return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
