package com.rs.tool.chipannotation;

import java.util.List;

public class ImageContent {

    public String name;
    public String githubRepo;
    public int    githubIssueId;
    public String source;

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
