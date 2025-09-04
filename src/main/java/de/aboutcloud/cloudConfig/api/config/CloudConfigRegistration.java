package de.aboutcloud.cloudConfig.api.config;

import java.util.List;
import java.util.Objects;

public final class CloudConfigRegistration {
    private final List<String> configFiles; // e.g., ["config.yml","database.yml"]
    private final String defaultLocaleTag;  // e.g., "en-EN"
    private final String defaultLocaleFile; // e.g., "locale/en-EN.yml"
    private final boolean copyDefaultsIfMissing;

    public CloudConfigRegistration(List<String> configFiles,
                                   String defaultLocaleTag,
                                   String defaultLocaleFile,
                                   boolean copyDefaultsIfMissing) {
        this.configFiles = Objects.requireNonNull(configFiles);
        this.defaultLocaleTag = Objects.requireNonNull(defaultLocaleTag);
        this.defaultLocaleFile = Objects.requireNonNull(defaultLocaleFile);
        this.copyDefaultsIfMissing = copyDefaultsIfMissing;
    }
    public List<String> configFiles() { return configFiles; }
    public String defaultLocaleTag() { return defaultLocaleTag; }
    public String defaultLocaleFile() { return defaultLocaleFile; }
    public boolean copyDefaultsIfMissing() { return copyDefaultsIfMissing; }
}
