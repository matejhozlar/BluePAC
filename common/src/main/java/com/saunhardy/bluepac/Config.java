package com.saunhardy.bluepac;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.saunhardy.bluepac.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private Config() {}

    private static final String FILE_NAME = "bluepac-server.toml";

    private static double fillOpacity = 0.3;
    private static double lineOpacity = 0.8;
    private static int lineWidth = 2;
    private static int markerYHeight = 64;

    public static double fillOpacity() { return fillOpacity; }
    public static double lineOpacity() { return lineOpacity; }
    public static int lineWidth() { return lineWidth; }
    public static int markerYHeight() { return markerYHeight; }

    public static void load() {
        Path file = Services.INSTANCE.getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            BluePAC.LOGGER.error("Failed to create BluePAC config directory", e);
            return;
        }

        CommentedFileConfig config = CommentedFileConfig.builder(file)
                .autosave()
                .preserveInsertionOrder()
                .sync()
                .build();
        try {
            config.load();

            fillOpacity = clamp(getDouble(config, "markers.fillOpacity", fillOpacity), 0.0, 1.0);
            config.set("markers.fillOpacity", fillOpacity);
            config.setComment("markers.fillOpacity",
                    " Fill opacity of claim markers.\n Range: 0.0 (transparent) - 1.0 (opaque)");

            lineOpacity = clamp(getDouble(config, "markers.lineOpacity", lineOpacity), 0.0, 1.0);
            config.set("markers.lineOpacity", lineOpacity);
            config.setComment("markers.lineOpacity",
                    " Line opacity of claim markers.\n Range: 0.0 (transparent) - 1.0 (opaque)");

            lineWidth = clamp(config.getIntOrElse("markers.lineWidth", lineWidth), 1, 10);
            config.set("markers.lineWidth", lineWidth);
            config.setComment("markers.lineWidth",
                    " Line width of claim markers in pixels.\n Range: 1-10");

            markerYHeight = clamp(config.getIntOrElse("markers.markerYHeight", markerYHeight), -64, 320);
            config.set("markers.markerYHeight", markerYHeight);
            config.setComment("markers.markerYHeight",
                    " Y height at which the flat claim markers are drawn on the map.\n Range: -64 - 320");

            config.save();
        } catch (Exception e) {
            BluePAC.LOGGER.error("Failed to load BluePAC config; using defaults", e);
        } finally {
            config.close();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Reads a numeric value as a double, tolerating TOML ints (e.g. {@code 1} for {@code 1.0}). */
    private static double getDouble(CommentedFileConfig config, String path, double def) {
        Object raw = config.get(path);
        return raw instanceof Number n ? n.doubleValue() : def;
    }
}
