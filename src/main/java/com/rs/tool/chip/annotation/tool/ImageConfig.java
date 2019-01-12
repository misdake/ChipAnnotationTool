package com.rs.tool.chip.annotation.tool;

import java.util.List;

public class ImageConfig {

    public String githubRepo;
    public int    githubIssueId;

    public int         width;
    public int         height;
    public int         tileSize;
    public int         maxLevel;
    public List<Level> levels;

    public double widthMillimeter;
    public double heightMillimeter;

    public static class Level {
        public int level;
        public int xMax;
        public int yMax;
    }
}
