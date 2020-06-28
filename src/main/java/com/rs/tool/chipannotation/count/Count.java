package com.rs.tool.chipannotation.count;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rs.tool.chipannotation.ChipList;
import com.rs.tool.chipannotation.ImageContent;
import com.rs.tool.chipannotation.log.GithubStructures;
import com.rs.tool.chipannotation.log.Http;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class Count {

    public static void main(String[] args) {
        ChipCount[] state = getState();
        new File("count").mkdirs();
        File stateFile = new File("count/" + day + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(state);
        try {
            Files.write(stateFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final static String day;

    static {
        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
        day = format.format(new Date());
    }

    private static class CountResult {
        public Integer value;
    }
    private static int getCount(String chip, long commentId) {
        CountResult countResult = Http.get("https://api.countapi.xyz/get/chipannotationviewer/" + chip + "_" + commentId, CountResult.class);
        return countResult != null && countResult.value != null ? countResult.value : Integer.valueOf(0);
    }
    private static AnnotationCount getAnnotationCount(String title, String username, String chip, long commentId, Map<String, Integer> lastMap) {
        int count = getCount(chip, commentId);
        AnnotationCount c = new AnnotationCount();
        c.id = commentId;
        c.title = title;
        c.username = username;
        c.count = count;
        c.fullId = chip + "_" + commentId;

        Integer lastCount = lastMap.get(c.fullId);
        if(lastCount == null) lastCount = 0;
        c.delta = count - lastCount;

        return c;
    }

    public static ChipCount[] getState() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
        String lastDay = format.format(new Date(new Date().getTime() - 1000 * 60 * 60 * 24));
        System.out.println("lastDay: "+lastDay);

        File lastFile = new File("count/" + lastDay + ".json");
        Map<String, Integer> lastMap = new HashMap<>();
        if (lastFile.exists()) {
            try {
                String str = new String(Files.readAllBytes(lastFile.toPath()), StandardCharsets.UTF_8);
                ChipCount[] array = gson.fromJson(str, ChipCount[].class);
                for (ChipCount chipCount : array) {
                    for (AnnotationCount annotation : chipCount.annotations) {
                        annotation.fullId = chipCount.chipName + "_" + annotation.id;
                        annotation.delta = annotation.count;
                        lastMap.put(annotation.fullId, annotation.count);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<ChipCount> result = new ArrayList<>();

        System.out.println("get image list");
        String json = Http.get("https://misdake.github.io/ChipAnnotationData/list.json");
        if (json == null) return null;
        ChipList.Chip[] chipList = gson.fromJson(json, ChipList.Chip[].class);
        for (int i = 0; i < chipList.length; i++) {
            ChipList.Chip chip = chipList[i];
            System.out.print("  image: " + chip.name + "  " + (i + 1) + "/" + chipList.length);
            String contentUrl = chip.url + "/content.json";
            ImageContent image = Http.get(contentUrl, ImageContent.class);
            if (image == null) return null;

            ChipCount chipCount = new ChipCount();
            result.add(chipCount);
            chipCount.chipName = chip.name;
            List<AnnotationCount> annotationCounts = new ArrayList<>();
            AnnotationCount c0 = getAnnotationCount("(empty)", "", chip.name, 0, lastMap);
            annotationCounts.add(c0);
            System.out.println(" -> " + c0.count);

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
                        AnnotationCount c = getAnnotationCount(commentBody.title, comment.user.login, chip.name, comment.id, lastMap);
                        annotationCounts.add(c);
                        System.out.println("  '" + commentBody.title + "' by " + comment.user.login + " -> " + c.count);
                        valid = true;
                    }
                } catch (Exception ignored) {
                }

                if (!valid) {
                    System.out.println("  (not annotation)");
                }
            }

            chipCount.annotations = annotationCounts.toArray(new AnnotationCount[0]);
        }

        return result.toArray(new ChipCount[0]);
    }


}
