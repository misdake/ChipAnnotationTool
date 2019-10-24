package com.rs.tool.chipannotation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ChipList {

    public static class Chip {
        public final String vendor;
        public final String type;
        public final String family;
        public final String name;
        public final String url;

        private transient String id;

        public Chip(String vendor, String type, String family, String name, String url) {
            this.vendor = vendor;
            this.type = type;
            this.family = family;
            this.name = name;
            this.url = url;
        }

        private void checkId() {
            if (id == null) {
                id = type + " " + vendor + " " + family + " " + name;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Chip)) return false;
            this.checkId();
            ((Chip) obj).checkId();
            return this.id.equals(((Chip) obj).id);
        }
    }


    private final String filename;
    private final List<Chip> chips = new ArrayList<>();

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

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
            if (c.equals(chip)) {
                return;
            }
        }
        chips.add(chip);
    }

    public boolean save() {
        chips.sort(Comparator.comparing(o -> o.id));
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
