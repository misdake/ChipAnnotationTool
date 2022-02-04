package com.rs.tool.chipannotation.log;

import com.google.gson.Gson;
import com.rs.tool.chipannotation.ChipList;
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

    private static Date lastDate(Date curr, Date input) {
        if (curr == null) return input;
        if (curr.before(input)) {
            return input;
        } else {
            return curr;
        }
    }

    public ImageEntry[] images;
    public Date time;

    public static State getState() {
        List<ImageEntry> imageList = new ArrayList<>();

        Date last = null;

        System.out.println("get image list");
        String json = Http.get("https://misdake.github.io/ChipAnnotationList/list.json");
        if (json == null) return null;
        Gson gson = new Gson();
        ChipList.Chip[] chipList = gson.fromJson(json, ChipList.Chip[].class);
        for (int i = 0; i < chipList.length; i++) {
            ChipList.Chip chip = chipList[i];
            System.out.println("  image: " + chip.name + "  " + (i + 1) + "/" + chipList.length);
            String contentUrl = chip.url + "/content.json";
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

                        last = lastDate(last, comment.created_at);
                        last = lastDate(last, comment.updated_at);

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

            last = lastDate(last, image.createTime);

            imageList.add(imageEntry);
        }

        State state = new State();
        state.images = imageList.toArray(new ImageEntry[0]);
        state.time = last;

        return state;
    }


}
