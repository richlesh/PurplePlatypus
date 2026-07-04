/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;

import java.io.File;
import java.io.IOException;

/**
 * Manages the JCEF CefApp lifecycle. Call {@link #initialize()} at startup
 * and {@link #shutdown()} when the application exits.
 */
public class CefAppManager {

    private static CefApp cefApp;
    private static CefClient cefClient;
    private static volatile boolean initialized = false;

    /**
     * Initializes the JCEF framework. Must be called once at application startup.
     * This method blocks until initialization is complete.
     */
    public static synchronized void initialize() {
        if (initialized) return;

        try {
            CefAppBuilder builder = new CefAppBuilder();

            // Store JCEF bundle in app support directory
            String userHome = System.getProperty("user.home");
            String os = System.getProperty("os.name", "").toLowerCase();
            File installDir;
            if (os.contains("mac")) {
                installDir = new File(userHome, "Library/Application Support/PurplePlatypus/jcef-bundle");
            } else if (os.contains("win")) {
                String appData = System.getenv("LOCALAPPDATA");
                if (appData == null) appData = userHome;
                installDir = new File(appData, "PurplePlatypus/jcef-bundle");
            } else {
                installDir = new File(userHome, ".local/share/PurplePlatypus/jcef-bundle");
            }
            builder.setInstallDir(installDir);

            // Configure settings
            builder.getCefSettings().windowless_rendering_enabled = false;
            builder.getCefSettings().log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE;

            // App handler
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {
                @Override
                public void stateHasChanged(CefApp.CefAppState state) {
                    if (state == CefApp.CefAppState.TERMINATED) {
                        // Allow JVM to exit
                    }
                }
            });

            // Build and initialize
            cefApp = builder.build();
            cefClient = cefApp.createClient();
            initialized = true;

        } catch (IOException | UnsupportedPlatformException | InterruptedException |
                 CefInitializationException e) {
            System.err.println("Failed to initialize JCEF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns the shared CefClient, or null if not yet initialized.
     */
    public static CefClient getClient() {
        return cefClient;
    }

    /**
     * Returns the CefApp instance, or null if not yet initialized.
     */
    public static CefApp getApp() {
        return cefApp;
    }

    /**
     * Returns true if JCEF has been successfully initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Shuts down the JCEF framework. Call when the application is exiting.
     */
    public static synchronized void shutdown() {
        if (!initialized) return;
        if (cefApp != null) {
            cefApp.dispose();
            cefApp = null;
            cefClient = null;
            initialized = false;
        }
    }
}
