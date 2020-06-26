package com.rs.tool.chipannotation.count;

class ChipCount {
    public String            chipName;
    public AnnotationCount[] annotations;
}

class AnnotationCount {
    public long   id;
    public String title;
    public String username;
    public String day;

    public int count;
}
