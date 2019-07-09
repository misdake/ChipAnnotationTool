package com.rs.tool.chipannotation.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Log {

    public static void main(String[] args) {
        System.out.println(formatter.format(new Date()));
        System.out.println(gson.toJson(new Date()));
    }

    public static final DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
    public static final Gson gson;

    static {
        formatter.setTimeZone(TimeZone.getTimeZone("GMT+:08:00"));
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+:08:00"));

        RuntimeTypeAdapterFactory<Log> adapterFactory = RuntimeTypeAdapterFactory.of(Log.class, "type")
                .registerSubtype(Log.ImageCreate.class, "IMAGE_CREATE")
                .registerSubtype(Log.CommentInsert.class, "COMMENT_INSERT")
                .registerSubtype(Log.CommentUpdate.class, "COMMENT_UPDATE");

        gson = new GsonBuilder().setPrettyPrinting().setDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").registerTypeAdapterFactory(adapterFactory).create();
    }

    public enum LogType {
        IMAGE_CREATE,
        COMMENT_INSERT,
        COMMENT_UPDATE,
    }

    public LogType logType;
    public Date time;
    public String dateString;

    private final static SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");

    public Log(LogType logType, Date time) {
        this.logType = logType;
        this.time = time;
        dateString = format.format(time);
    }

    public static class ImageCreate extends Log {
        public String name;
        public String githubRepo;
        public int githubIssueId;
        public Date insertTime;

        public ImageCreate(State.ImageEntry image) {
            super(LogType.IMAGE_CREATE, image.insertTime);
            this.name = image.name;
            this.githubRepo = image.githubRepo;
            this.githubIssueId = image.githubIssueId;
            this.insertTime = image.insertTime;
        }

        @Override
        public String toString() {
            return "add image: " + this.name;
        }
    }

    public static class CommentInsert extends Log {
        public String imageName;
        public String title;
        public String username;
        public String githubRepo;
        public int githubIssueId;
        public long commentId;
        public Date insertTime;
        public Date updateTime;

        public CommentInsert(State.CommentEntry comment, State.ImageEntry image) {
            super(LogType.COMMENT_INSERT, comment.insertTime);
            this.imageName = image.name;
            this.title = comment.title;
            this.username = comment.username;
            this.githubRepo = image.githubRepo;
            this.githubIssueId = image.githubIssueId;
            this.commentId = comment.commentId;
            this.insertTime = comment.insertTime;
            this.updateTime = comment.updateTime;
        }

        @Override
        public String toString() {
            return "add comment: '" + this.title + "' to " + this.imageName + " by " + this.username;
        }
    }

    public static class CommentUpdate extends Log {
        public String imageName;
        public String title;
        public String username;
        public String githubRepo;
        public int githubIssueId;
        public long commentId;
        public Date insertTime;
        public Date updateTime;

        public CommentUpdate(State.CommentEntry comment, State.ImageEntry image) {
            super(LogType.COMMENT_UPDATE, comment.updateTime);
            this.imageName = image.name;
            this.title = comment.title;
            this.username = comment.username;
            this.githubRepo = image.githubRepo;
            this.githubIssueId = image.githubIssueId;
            this.commentId = comment.commentId;
            this.insertTime = comment.insertTime;
            this.updateTime = comment.updateTime;
        }

        @Override
        public String toString() {
            return "update comment: '" + this.title + "' to " + this.imageName + " by " + this.username;
        }
    }
}
