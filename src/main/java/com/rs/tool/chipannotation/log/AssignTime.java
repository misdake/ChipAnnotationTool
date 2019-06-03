//package com.rs.tool.chipannotation.log;
//
//import com.google.gson.Gson;
//import com.google.gson.GsonBuilder;
//import com.rs.tool.chipannotation.ImageContent;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.StandardOpenOption;
//import java.util.Date;
//import java.util.List;
//
//public class AssignTime {
//
//    public static void main(String[] args) throws IOException {
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        List<String> lines = Files.readAllLines(new File("log/time.txt").toPath(), StandardCharsets.UTF_8);
//        for (String line : lines) {
//            int i = line.indexOf('\t');
//            String time = line.substring(0, i);
//            String names = line.substring(i + 1);
//            String[] nameArray = names.split(" ");
//            Date date = gson.fromJson("'" + time + "'", Date.class);
//
//
//            for (String name : nameArray) {
//                System.out.println(name);
//
//                File file = new File("D:/ChipAnnotationData/" + name + "/content.json");
//                byte[] input = Files.readAllBytes(file.toPath());
//                String content = new String(input, StandardCharsets.UTF_8);
//                ImageContent imageContent = gson.fromJson(content, ImageContent.class);
//                imageContent.createTime = date;
//
//                String json = gson.toJson(imageContent);
//                byte[] output = json.getBytes(StandardCharsets.UTF_8);
//                Files.write(file.toPath(), output, StandardOpenOption.CREATE);
//            }
//        }
//
//        System.out.println();
//
//    }
//
//}

//time.txt data: (extracted from git log)
//2019-04-26T00:56:19+08:00	NV40 G70 G71 RSX
//2019-04-26T00:44:01+08:00	R430 RV670
//2019-04-26T00:31:18+08:00	Cayman Pitcairn
//2019-04-26T00:27:55+08:00	R520 R580 RV570
//2019-04-06T00:06:41+08:00	Tahiti
//2019-04-05T23:57:36+08:00	PS4 PS4Pro
//2019-04-05T23:42:37+08:00	GF114 GK106
//2019-04-05T23:37:17+08:00	GF106
//2019-04-05T23:31:01+08:00	GK104
//2019-04-05T23:29:30+08:00	G94
//2019-04-05T23:27:39+08:00	G92
//2019-04-05T23:26:06+08:00	G80
//2019-03-07T14:18:03+08:00	TU102
//2019-03-05T21:26:54+08:00	TU106 TU116
//2019-02-17T20:12:20+08:00	Polaris22
//2019-01-06T15:38:32+08:00	Polaris10 Polaris11 Vega10
//2019-01-06T15:31:49+08:00	Fiji Hawaii
//2019-01-06T15:15:31+08:00	GK110 GT200
//2019-01-05T15:59:16+08:00	GM200 GP102 GP104 GP106
//2018-09-17T09:19:49+08:00	Hawaii
//2018-02-05T01:18:00+08:00	Fiji