/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Ensures only a single instance of PurplePlatypus runs at a time.
 * Uses a local server socket to detect existing instances and pass
 * file open requests to the running instance.
 */
public class SingleInstance {

    private static final int PORT = 52718; // Arbitrary fixed port for IPC
    private static ServerSocket serverSocket;

    /**
     * Attempts to become the single instance. If another instance is already
     * running, sends the file paths to it and returns false.
     *
     * @param args command-line arguments (file paths to open)
     * @return true if this is the first instance, false if another is running
     */
    public static boolean tryAcquire(String[] args) {
        try {
            // Try to bind the server socket — if it succeeds, we're the first instance
            serverSocket = new ServerSocket(PORT, 0, InetAddress.getLoopbackAddress());
            startListener();
            return true;
        } catch (IOException e) {
            // Port already in use — another instance is running
            sendFilesToExisting(args);
            return false;
        }
    }

    /**
     * Starts a background thread that listens for file open requests
     * from subsequent instances.
     */
    private static void startListener() {
        Thread listener = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String filePath = line.trim();
                        if (!filePath.isEmpty()) {
                            File file = new File(filePath);
                            if (file.exists()) {
                                SwingUtilities.invokeLater(() -> EditorWindow.openFileInWindow(file));
                            }
                        } else {
                            // Empty line = bring existing window to front
                            SwingUtilities.invokeLater(() -> {
                                EditorWindow active = EditorWindow.getActiveInstance();
                                if (active != null) {
                                    active.getFrame().toFront();
                                    active.getFrame().setState(java.awt.Frame.NORMAL);
                                    active.getFrame().requestFocus();
                                }
                            });
                        }
                    }
                    client.close();
                } catch (IOException e) {
                    if (serverSocket.isClosed()) break;
                    // Continue listening
                }
            }
        }, "SingleInstance-Listener");
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Sends file paths to the existing instance and brings it to the front.
     *
     * @param args command-line arguments (file paths)
     */
    private static void sendFilesToExisting(String[] args) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), PORT);
             PrintWriter writer = new PrintWriter(
                 new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            if (args.length > 0) {
                for (String arg : args) {
                    writer.println(new File(arg).getAbsolutePath());
                }
            } else {
                // No files — just signal to bring existing window to front
                writer.println("");
            }
        } catch (IOException e) {
            // Couldn't connect — fall through and let the app start anyway
        }
    }
}
