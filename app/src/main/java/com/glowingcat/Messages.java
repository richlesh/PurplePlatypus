/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Provides localized strings for the PurplePlatypus UI.
 * <p>
 * Usage: {@code Messages.get("menu.file")} returns the localized string for the given key.
 * Parameterized messages: {@code Messages.get("msg.matches", count)} substitutes {0} with count.
 */
public class Messages {

    private static final String BUNDLE_NAME = "com.glowingcat.Messages";
    private static ResourceBundle bundle;
    private static Locale currentLocale;

    static {
        setLocale(Locale.getDefault());
    }

    private Messages() {}

    /**
     * Sets the locale for all UI strings. Call before building the UI.
     *
     * @param locale the desired locale
     */
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }

    /**
     * Returns the current locale.
     */
    public static Locale getLocale() {
        return currentLocale;
    }

    /**
     * Returns the localized string for the given key.
     * If the key is not found, returns the key itself (for development visibility).
     *
     * @param key the resource key
     * @return the localized string
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Returns the localized string for the given key with parameter substitution.
     *
     * @param key  the resource key
     * @param args values to substitute for {0}, {1}, etc.
     * @return the formatted localized string
     */
    public static String get(String key, Object... args) {
        String pattern = get(key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }
}
