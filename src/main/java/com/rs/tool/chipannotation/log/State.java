package com.rs.tool.chipannotation.log;

import com.rs.tool.chipannotation.ImageContent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class State {

    public static class ImageEntry {
        public String name;
        public String githubRepo;
        public int githubIssueId;
        public Date insertTime;
        public CommentEntry[] comments;
    }

    public static class CommentEntry {
        public String title;
        public String username;
        public long commentId;
        public Date insertTime;
        public Date updateTime;
    }


    public ImageEntry[] images;
    public Date time;

    public static State getState() {
        List<ImageEntry> imageList = new ArrayList<>();

        System.out.println("get image list");
        String list = Http.get("https://misdake.github.io/ChipAnnotationData/list.txt");
        if (list == null) return null;
        String[] lines = list.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            System.out.println("  image: " + line.substring(line.lastIndexOf('/') + 1) + "  " + (i + 1) + "/" + lines.length);
            String contentUrl = line + "/content.json";
            ImageContent image = Http.get(contentUrl, ImageContent.class);
            if (image == null) return null;

            List<CommentEntry> commentList = new ArrayList<>();

            String repo = image.githubRepo;
            int issueId = image.githubIssueId;
            String commentsUrl = "https://api.github.com/repos/" + repo + "/issues/" + issueId + "/comments";
            GithubStructures.Comment[] comments = Http.get(commentsUrl, GithubStructures.Comment[].class);
            if (comments == null) return null;

            for (int j = 0; j < comments.length; j++) {
                System.out.print("    comment: " + (j + 1) + "/" + comments.length);
                GithubStructures.Comment comment = comments[j];
                boolean valid = false;
                try {
                    GithubStructures.CommentBody commentBody = Http.gson.fromJson(comment.body, GithubStructures.CommentBody.class);
                    if (commentBody != null && commentBody.title != null) {
                        CommentEntry commentEntry = new CommentEntry();
                        commentEntry.title = commentBody.title;
                        commentEntry.username = comment.user.login;
                        commentEntry.commentId = comment.id;
                        commentEntry.insertTime = comment.created_at;
                        commentEntry.updateTime = comment.updated_at;

                        System.out.println("  '" + commentEntry.title + "' by " + commentEntry.username);
                        commentList.add(commentEntry);
                        valid = true;
                    }
                } catch (Exception ignored) {
                }

                if (!valid) {
                    System.out.println("  (not annotation)");
                }
            }


            ImageEntry imageEntry = new ImageEntry();
            imageEntry.name = image.name;
            imageEntry.githubRepo = image.githubRepo;
            imageEntry.githubIssueId = image.githubIssueId;
            imageEntry.insertTime = image.createTime;
            imageEntry.comments = commentList.toArray(new CommentEntry[0]);

            imageList.add(imageEntry);
        }

        State state = new State();
        state.images = imageList.toArray(new ImageEntry[0]);
        state.time = new Date();

        return state;
    }

    public static void main(String[] args) {
        File logFolder = new File("log");
        logFolder.mkdirs();

        State state = getState();
        if (state != null) {
            File stateFile = new File("log/state_" + state.time.getTime() + ".json");
            String json = Http.gson.toJson(state);
            try {
                Files.write(stateFile.toPath(), json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
