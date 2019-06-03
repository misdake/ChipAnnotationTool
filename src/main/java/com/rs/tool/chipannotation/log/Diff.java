package com.rs.tool.chipannotation.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class Diff {

    public enum LogType {
        IMAGE_CREATE,
        COMMENT_INSERT,
        COMMENT_UPDATE,
    }

    public static class Log {
        public LogType logType;
        public Date time;
        public int year;
        public int month;
        public int day;
        public String dateString;

        private final static SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");

        public Log(LogType logType, Date time) {
            this.logType = logType;
            this.time = time;

            Calendar calendar = new GregorianCalendar();
            calendar.setTime(time);
            this.year = calendar.get(Calendar.YEAR);
            this.month = calendar.get(Calendar.MONTH) + 1;
            this.day = calendar.get(Calendar.DAY_OF_MONTH);

            dateString = format.format(time);
        }
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
    }

    public static class CommentInsert extends Log {
        public String imageName;
        public String title;
        public String username;
        public long commentId;
        public Date insertTime;
        public Date updateTime;

        public CommentInsert(State.CommentEntry comment, String imageName) {
            super(LogType.COMMENT_INSERT, comment.insertTime);
            this.imageName = imageName;
            this.title = comment.title;
            this.username = comment.username;
            this.commentId = comment.commentId;
            this.insertTime = comment.insertTime;
            this.updateTime = comment.updateTime;
        }
    }

    public static class CommentUpdate extends Log {
        public String imageName;
        public String title;
        public String username;
        public long commentId;
        public Date insertTime;
        public Date updateTime;

        public CommentUpdate(State.CommentEntry comment, String imageName) {
            super(LogType.COMMENT_UPDATE, comment.updateTime);
            this.imageName = imageName;
            this.title = comment.title;
            this.username = comment.username;
            this.commentId = comment.commentId;
            this.insertTime = comment.insertTime;
            this.updateTime = comment.updateTime;
        }
    }

    public static Log[] diff(State prev, State next) {
        Map<String, State.ImageEntry> images = new HashMap<>();
        Map<State.CommentEntry, State.ImageEntry> parents = new HashMap<>();
        Map<Long, State.CommentEntry> comments = new HashMap<>();

        for (State.ImageEntry image : prev.images) {
            images.put(image.name, image);
            for (State.CommentEntry comment : image.comments) {
                parents.put(comment, image);
                comments.put(comment.commentId, comment);
            }
        }

        List<Log> logs = new ArrayList<>();

        for (State.ImageEntry image : next.images) {
            //check image
            State.ImageEntry oldImage = images.get(image.name);
            if (oldImage == null) {
                logs.add(new ImageCreate(image));
            }

            for (State.CommentEntry comment : image.comments) {
                //check comment
                State.CommentEntry oldComment = comments.get(comment.commentId);
                if (oldComment == null) {
                    logs.add(new CommentInsert(comment, image.name));
                } else if (oldComment.updateTime.before(comment.updateTime)) {
                    logs.add(new CommentUpdate(comment, image.name));
                }
            }
        }

        return logs.toArray(new Log[0]);
    }

    public static void main(String[] args) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String s1 = new String(Files.readAllBytes(new File("log/state_old.json").toPath()), StandardCharsets.UTF_8);
        String s2 = new String(Files.readAllBytes(new File("log/state_new.json").toPath()), StandardCharsets.UTF_8);
        State state_old = gson.fromJson(s1, State.class);
        State state_new = gson.fromJson(s2, State.class);
        Log[] diff = diff(state_old, state_new);
        System.out.println(gson.toJson(diff));
    }

}
