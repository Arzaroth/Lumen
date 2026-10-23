package com.arzaroth.lumen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A local stand-in for GitHub: the latest release and its APK, both swappable per test. */
final class Releases implements AutoCloseable {
    private final ServerSocket server;
    final String base;
    volatile int latestStatus = 200, apkStatus = 200;
    volatile String latest;
    volatile byte[] apk = new byte[300_000];

    Releases(String tag) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        base = "http://127.0.0.1:" + server.getLocalPort();
        release(tag, "lumen.apk");
        Thread t = new Thread(this::serve, "releases");
        t.setDaemon(true);
        t.start();
        Updater.releases = base + "/latest";
    }

    void release(String tag, String asset) {
        latest = "{\"tag_name\":\"" + tag + "\",\"assets\":[{\"name\":\"notes.txt\",\"browser_download_url\":\"" + base
                + "/notes.txt\"},{\"name\":\"" + asset + "\",\"browser_download_url\":\"" + base + "/lumen.apk\"}]}";
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                String path = in.readLine().split(" ")[1];
                for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                    continue;
                }
                boolean isApk = path.equals("/lumen.apk");
                int status = isApk ? apkStatus : latestStatus;
                byte[] body = status != 200 ? new byte[0] : isApk ? apk : latest.getBytes(StandardCharsets.UTF_8);
                OutputStream out = s.getOutputStream();
                out.write(("HTTP/1.1 " + status + " X\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
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
