/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * Main.java
 *
 * Entry point for the PurplePlatypus application. Sets up system properties,
 * look and feel, JavaFX initialization, and macOS Desktop handlers.
 */
package com.glowingcat;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application entry point for PurplePlatypus.
 */
public class Main {

    /** Set when the OS delivers an open-file event (e.g. double-clicking a .md file). */
    private static final AtomicBoolean fileOpenRequested = new AtomicBoolean(false);

    /**
     * Application entry point. Sets up the platform, registers macOS handlers,
     * and opens the first editor window.
     */
    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PurplePlatypus");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default cross-platform L&F
        }

        // Initialize JavaFX toolkit and prevent it from exiting when windows close
        new JFXPanel();
        Platform.setImplicitExit(false);

        // Register macOS application menu handlers
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler(e -> {
                    EditorWindow active = EditorWindow.getActiveInstance();
                    if (active != null) active.showAboutDialog();
                });
            }
            if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
                desktop.setPreferencesHandler(e -> {
                    EditorWindow active = EditorWindow.getActiveInstance();
                    if (active != null) active.showPreferencesDialog();
                });
            }
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((e, response) -> {
                    for (EditorWindow instance : new ArrayList<>(EditorWindow.openInstances)) {
                        if (!instance.confirmClose()) {
                            response.cancelQuit();
                            return;
                        }
                    }
                    response.performQuit();
                });
            }
            if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
                desktop.setOpenFileHandler(e -> {
                    fileOpenRequested.set(true);
                    for (File file : e.getFiles()) {
                        SwingUtilities.invokeLater(() -> EditorWindow.openFileInWindow(file));
                    }
                });
            }
        }

        // Open file from command-line argument, or create empty window
        SwingUtilities.invokeLater(() -> {
            // Show splash screen if not licensed
            Preferences prefs = Preferences.load();
            if (!LicenseDialog.isLicensed(prefs)) {
                SplashScreen.show();
            }

            if (args.length > 0) {
                File file = new File(args[0]);
                if (file.exists()) {
                    EditorWindow.openFileInWindow(file);
                } else {
                    new EditorWindow();
                }
            } else if (!fileOpenRequested.get()) {
                // Only create an empty window if not launched by double-clicking a document
                new EditorWindow();
            }
        });
    }
}
