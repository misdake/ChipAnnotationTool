package com.rs.tool.chipannotation;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChipList {

    public enum ChipType {
        CPU, GPU, SOC,
    }

    public static class Chip {
        public final String vendor;
        public final ChipType type;
        public final String name;
        public final String url;

        public final String id;

        public Chip(String vendor, ChipType type, String name, String url) {
            this.vendor = vendor;
            this.type = type;
            this.name = name;
            this.url = url;
            this.id = type.name() + " " + vendor + " " + name;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Chip)) return false;
            return this.id.equals(((Chip) obj).id);
        }
    }


    private final String filename;
    private final List<Chip> chips = new ArrayList<>();

    private static final Gson gson = new Gson();

    public ChipList(String filename) {
        this.filename = filename;
    }

    public boolean load() {
        try {
            Chip[] array = gson.fromJson(new String(Files.readAllBytes(new File(filename).toPath())), Chip[].class);
            chips.addAll(Arrays.asList(array));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void add(Chip chip) {
        for (Chip c : chips) {
            if (c.id.equals(chip.id)) {
                return;
            }
        }
        chips.add(chip);
    }

    public boolean save() {
        String s = gson.toJson(chips);
        try {
            Files.write(new File(filename).toPath(), s.getBytes(), StandardOpenOption.CREATE);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
