package com.rs.tool.chipannotation;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AppConfig {

    private String listFolder;
    private String imageFolder;

    public String getListFolder() {
        return listFolder;
    }

    public void setListFolder(String listFolder) {
        this.listFolder = listFolder;
        write();
    }

    public String getImageFolder() {
        return imageFolder;
    }

    public void setImageFolder(String imageFolder) {
        this.imageFolder = imageFolder;
        write();
    }


    private static final File configFile = new File("config.cfg");
    private static final Gson gson = new Gson();

    private static void read() {
        if (configFile.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(configFile.toPath());
                instance = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), AppConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (instance == null) {
            instance = new AppConfig();
        }
    }

    private static void write() {
        String json = gson.toJson(instance);
        try {
            Files.write(configFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AppConfig instance() {
        return instance;
    }

    private static AppConfig instance;

    static {
        read();
    }

}
