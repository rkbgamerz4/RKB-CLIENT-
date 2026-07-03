package com.rkb.client.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigManager {

    private final File configDir = new File("rkbclient");
    private final File configFile = new File(configDir, "config.properties");
    private final Properties properties = new Properties();

    public void init() throws IOException {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        if (!configFile.exists()) {
            configFile.createNewFile();
            save();
        }

        load();
    }

    public void save() throws IOException {
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            properties.store(out, "RKB Client Config");
        }
    }

    public void load() throws IOException {
        try (FileInputStream in = new FileInputStream(configFile)) {
            properties.load(in);
        }
    }

    public void set(String key, String value) {
        properties.setProperty(key, value);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
