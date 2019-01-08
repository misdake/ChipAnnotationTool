package com.rs.tool.chip.annotation.tool;

import com.google.gson.Gson;
import org.imgscalr.Scalr;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

public class Split {

    public static void main(String[] args) throws IOException {
        assure(args.length >= 1, "usage: fileName [targetFolder] [width(mm) height(mm)]");

        String fileName = args[0];
        File sourceFile = new File(fileName);
        assure(sourceFile.exists() && sourceFile.isFile(), "file not found: " + fileName);

        String targetFolderName = args.length >= 2 ? args[1] : System.getProperty("user.dir");
        File targetFolder = new File(targetFolderName);
        assure((targetFolder.exists() && targetFolder.isDirectory()) || targetFolder.mkdirs(), "cannot create folder: " + targetFolderName);

        String widthString = args.length >= 4 ? args[2] : "-1";
        String heightString = args.length >= 4 ? args[3] : "-1";
        double width = Double.parseDouble(widthString);
        double height = Double.parseDouble(heightString);

        System.out.println("read file: " + fileName);
        BufferedImage sourceImage = null;
        try { sourceImage = ImageIO.read(sourceFile); } catch (IOException ignored) { }
        assure(sourceImage != null, "cannot read image");

        ImageConfig imageConfig = split(sourceImage, targetFolder);
        imageConfig.widthMillimeter = width;
        imageConfig.heightMillimeter = height;

        Files.write(new File(targetFolderName + "/content.json").toPath(), new Gson().toJson(imageConfig).getBytes(), StandardOpenOption.CREATE);
    }

    private static ImageConfig split(BufferedImage sourceImage, File targetFolder) {
        ImageConfig imageConfig = new ImageConfig();
        imageConfig.tileSize = 512;
        imageConfig.width = sourceImage.getWidth();
        imageConfig.height = sourceImage.getHeight();
        imageConfig.levels = new ArrayList<>();

        int currentLevel = 0;
        int currentWidth = imageConfig.width;
        int currentHeight = imageConfig.height;
        while (currentWidth > imageConfig.tileSize / 2 || currentHeight > imageConfig.tileSize / 2) {
            int xCount = (imageConfig.width - 1) / (imageConfig.tileSize << currentLevel) + 1;
            int yCount = (imageConfig.height - 1) / (imageConfig.tileSize << currentLevel) + 1;
            ImageConfig.Level zoomLevel = new ImageConfig.Level();
            zoomLevel.level = currentLevel;
            zoomLevel.xMax = xCount;
            zoomLevel.yMax = yCount;
            imageConfig.levels.add(zoomLevel);

            currentLevel++;
            currentWidth = (currentWidth + 1) / 2;
            currentHeight = (currentHeight + 1) / 2;
        }
        imageConfig.maxLevel = currentLevel - 1;

        sourceImage = expand(sourceImage, imageConfig.tileSize << imageConfig.maxLevel, imageConfig.tileSize << imageConfig.maxLevel);

        for (int i = 0; i <= imageConfig.maxLevel; i++) {
            System.gc();

            File zoomFolder = new File(targetFolder.getAbsolutePath() + "/" + i);
            boolean success = (zoomFolder.exists() && zoomFolder.isDirectory()) || zoomFolder.mkdirs();
            assure(success, "cannot create zoom folder");

            ImageConfig.Level level = imageConfig.levels.get(i);
            System.out.println("resizing level: " + i);
            BufferedImage cutImage = i == 0 ? sourceImage : Scalr.resize(sourceImage, Scalr.Method.QUALITY, imageConfig.tileSize << (imageConfig.maxLevel - i), imageConfig.tileSize << (imageConfig.maxLevel - i));

            System.out.println("writing level: " + i);
            for (int x = 0; x < level.xMax; x++) {
                for (int y = 0; y < level.yMax; y++) {
                    split(cutImage, imageConfig, x, y, zoomFolder);
                }
            }
        }

        return imageConfig;
    }
    private static void split(BufferedImage image, ImageConfig imageConfig, int x, int y, File zoomFolder) {
        int left = x * imageConfig.tileSize;
        int top = y * imageConfig.tileSize;
        int w = imageConfig.tileSize;
        int h = imageConfig.tileSize;
        boolean expand = false;
        if (left + w > image.getWidth()) { w = image.getWidth() - left; expand = true; }
        if (top + h > image.getHeight()) { h = image.getHeight() - top; expand = true; }
        BufferedImage subImage = image.getSubimage(left, top, w, h);
        if (expand) {
            subImage = expand(subImage, imageConfig.tileSize, imageConfig.tileSize);
        }
        String name = String.format("%s/%d_%d.jpg", zoomFolder.getAbsolutePath(), x, y);
        try {
            writeImage(subImage, new File(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void assure(boolean expression, String orelse) {
        if (!expression) {
            System.out.println(orelse);
            System.exit(1);
        }
    }

    private static BufferedImage expand(BufferedImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private static JPEGImageWriteParam jpegParams;

    static {
        jpegParams = new JPEGImageWriteParam(null);
        jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegParams.setCompressionQuality(0.9f);
    }

    private static void writeImage(BufferedImage image, File file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        writer.setOutput(new FileImageOutputStream(file));
        writer.write(null, new IIOImage(image, null, null), jpegParams);
    }
}
