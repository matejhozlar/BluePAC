package com.saunhardy.bluepac.platform;

import com.saunhardy.bluepac.BluePAC;

import java.nio.file.Path;
import java.util.ServiceLoader;

public interface Services {
    Services INSTANCE = load();

    /** The platform's config directory (e.g. {@code .minecraft/config}). */
    Path getConfigDir();

    /** Whether a mod with the given id is loaded on the current platform. */
    boolean isModLoaded(String modId);

    private static Services load() {
        Services service = ServiceLoader.load(Services.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No " + Services.class.getName() + " implementation found on classpath"));
        BluePAC.LOGGER.debug("Loaded platform services: {}", service.getClass().getName());
        return service;
    }
}
