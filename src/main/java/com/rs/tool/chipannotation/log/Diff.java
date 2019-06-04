package com.rs.tool.chipannotation.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class Diff {


    public static List<Log> diff(State prev, State next) {
        Map<String, State.ImageEntry> images = new HashMap<>();
        Map<Long, State.CommentEntry> comments = new HashMap<>();

        for (State.ImageEntry image : prev.images) {
            images.put(image.name, image);
            for (State.CommentEntry comment : image.comments) {
                comments.put(comment.commentId, comment);
            }
        }

        List<Log> logs = new ArrayList<>();

        for (State.ImageEntry image : next.images) {
            //check image
            State.ImageEntry oldImage = images.get(image.name);
            if (oldImage == null) {
                logs.add(new Log.ImageCreate(image));
            }

            for (State.CommentEntry comment : image.comments) {
                //check comment
                State.CommentEntry oldComment = comments.get(comment.commentId);
                if (oldComment == null) {
                    logs.add(new Log.CommentInsert(comment, image));
                    if (comment.insertTime.before(comment.updateTime)) {
                        logs.add(new Log.CommentUpdate(comment, image));
                    }
                } else if (oldComment.updateTime.before(comment.updateTime)) {
                    logs.add(new Log.CommentUpdate(comment, image));
                }
            }
        }

        return logs;
    }

    public static Log[] run(State prevState, State currState, Log[] prevLog) {
        List<Log> diff = diff(prevState, currState);
        List<Log> filtered = diff.stream().filter(log -> log.time.after(prevState.time)).collect(Collectors.toList());
        if (filtered.isEmpty()) return null;
        List<Log> all = new ArrayList<>(Arrays.asList(prevLog));
        all.addAll(filtered);
        all.sort(Comparator.comparing(o -> o.time));

        all.sort(Comparator.comparing(o -> o.time)); //sort here to make sure each sub list is sorted.
        if (all.size() > LOG_MAX) {
            all = all.subList(all.size() - LOG_MAX, all.size());
        }
        return all.toArray(new Log[0]);
    }

    private static Date getBeginOfDay(Date date) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.set(year, month, day);
        return new GregorianCalendar(year, month, day).getTime();
    }

    private static TreeMap<String, Log[]> groupByDay(Log[] all, Date timeMax) {
        TreeMap<String, List<Log>> groups = new TreeMap<>();
        for (Log log : all) {
            if (log.time.before(timeMax)) {
                List<Log> list = groups.computeIfAbsent(log.dateString, s -> new ArrayList<>());
                list.add(log);
            }
        }

        if (groups.keySet().size() > DAY_MAX) {
            List<String> days = new ArrayList<>(groups.keySet());
            for (int i = 0; i < days.size() - DAY_MAX; i++) {
                groups.remove(days.get(i));
            }
        }

        for (List<Log> list : groups.values()) {
            Set<Long> addedCommentId = new HashSet<>();
            for (Iterator<Log> iterator = list.iterator(); iterator.hasNext(); ) {
                Log log = iterator.next();
                switch (log.logType) {
                    case COMMENT_INSERT: {
                        long commentId = ((Log.CommentInsert) log).commentId;
                        addedCommentId.add(commentId); //remove update
                        break;
                    }
                    case COMMENT_UPDATE: {
                        long commentId = ((Log.CommentUpdate) log).commentId;
                        if (addedCommentId.contains(commentId)) {
                            iterator.remove();
                        }
                        addedCommentId.add(commentId); //remove more update
                        break;
                    }
                    default:
                }
            }
        }

        TreeMap<String, Log[]> r = new TreeMap<>();
        for (Map.Entry<String, List<Log>> e : groups.entrySet()) {
            r.put(e.getKey(), e.getValue().toArray(new Log[0]));
        }
        return r;
    }

    private final static int LOG_MAX = 1000;
    private final static int DAY_MAX = 20;

    public static void main(String[] args) throws IOException {
        RuntimeTypeAdapterFactory<Log> adapterFactory = RuntimeTypeAdapterFactory.of(Log.class, "type")
                .registerSubtype(Log.ImageCreate.class, "IMAGE_CREATE")
                .registerSubtype(Log.CommentInsert.class, "COMMENT_INSERT")
                .registerSubtype(Log.CommentUpdate.class, "COMMENT_UPDATE");

        Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapterFactory(adapterFactory).create();

        String statePrevString = new String(Files.readAllBytes(new File("log/state.json").toPath()), StandardCharsets.UTF_8);
        String logPrevString = new String(Files.readAllBytes(new File("log/log.json").toPath()), StandardCharsets.UTF_8);
        State statePrev = gson.fromJson(statePrevString, State.class);
        Log[] logPrev = gson.fromJson(logPrevString, Log[].class);

        State stateCurr = State.getState();
        if (stateCurr == null) return;

        Log[] logs = run(statePrev, stateCurr, logPrev);
        if (logs == null) {
            System.out.println("no change!");
            return;
        }

        Date beginOfDay = getBeginOfDay(stateCurr.time);
        TreeMap<String, Log[]> days = groupByDay(logs, beginOfDay);

        Files.write(new File("log/state.json").toPath(), gson.toJson(stateCurr).getBytes(StandardCharsets.UTF_8));
        Files.write(new File("log/log.json").toPath(), gson.toJson(logs).getBytes(StandardCharsets.UTF_8));
        Files.write(new File("log/log_day.json").toPath(), gson.toJson(days).getBytes(StandardCharsets.UTF_8));
    }

}
