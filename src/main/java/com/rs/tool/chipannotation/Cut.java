package com.rs.tool.chipannotation;

import org.imgscalr.Scalr;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Cut {

    public static ImageContent preProcess(BufferedImage sourceImage) {
        ImageContent imageContent = new ImageContent();
        imageContent.tileSize = 512;
        imageContent.width = sourceImage.getWidth();
        imageContent.height = sourceImage.getHeight();
        imageContent.levels = new ArrayList<>();

        int currentLevel = 0;
        int currentWidth = imageContent.width;
        int currentHeight = imageContent.height;
        while (currentWidth > imageContent.tileSize / 2 || currentHeight > imageContent.tileSize / 2) {
            int xCount = (imageContent.width - 1) / (imageContent.tileSize << currentLevel) + 1;
            int yCount = (imageContent.height - 1) / (imageContent.tileSize << currentLevel) + 1;
            ImageContent.Level zoomLevel = new ImageContent.Level();
            zoomLevel.level = currentLevel;
            zoomLevel.xMax = xCount;
            zoomLevel.yMax = yCount;
            imageContent.levels.add(zoomLevel);

            currentLevel++;
            currentWidth = (currentWidth + 1) / 2;
            currentHeight = (currentHeight + 1) / 2;
        }
        imageContent.maxLevel = currentLevel - 1;
        return imageContent;
    }

    public interface ProgressCallback {
        void beginLevel(int level);

        void doneResize(int level);

        void doneCut(int level, int x, int y);

        void alldone();

        void failed(String reason);
    }

    public static void cutAll(ImageContent imageContent, BufferedImage sourceImage, File targetFolder, ProgressCallback progressCallback) {
        sourceImage = expand(sourceImage, imageContent.tileSize << imageContent.maxLevel, imageContent.tileSize << imageContent.maxLevel);

        for (int i = 0; i <= imageContent.maxLevel; i++) {
            System.gc();

            progressCallback.beginLevel(i);

            File zoomFolder = new File(targetFolder.getAbsolutePath() + "/" + imageContent.name + "/" + i);
            boolean success = (zoomFolder.exists() && zoomFolder.isDirectory()) || zoomFolder.mkdirs();
            if (!success) {
                progressCallback.failed("cannot create zoom folder");
                return;
            }

            ImageContent.Level level = imageContent.levels.get(i);
            System.out.println("resizing level: " + i);
            BufferedImage cutImage = i == 0 ? sourceImage : Scalr.resize(sourceImage, Scalr.Method.QUALITY, imageContent.tileSize << (imageContent.maxLevel - i), imageContent.tileSize << (imageContent.maxLevel - i));
            progressCallback.doneResize(i);

            System.out.println("writing level: " + i);
            for (int x = 0; x < level.xMax; x++) {
                for (int y = 0; y < level.yMax; y++) {
                    cut(cutImage, imageContent, x, y, zoomFolder);
                    progressCallback.doneCut(i, x, y);
                }
            }
        }
        progressCallback.alldone();
    }

    private static void cut(BufferedImage image, ImageContent imageContent, int x, int y, File zoomFolder) {
        int left = x * imageContent.tileSize;
        int top = y * imageContent.tileSize;
        int w = imageContent.tileSize;
        int h = imageContent.tileSize;
        boolean expand = false;
        if (left + w > image.getWidth()) {
            w = image.getWidth() - left;
            expand = true;
        }
        if (top + h > image.getHeight()) {
            h = image.getHeight() - top;
            expand = true;
        }
        BufferedImage subImage = image.getSubimage(left, top, w, h);
        if (expand) {
            subImage = expand(subImage, imageContent.tileSize, imageContent.tileSize);
        }
        String name = String.format("%s/%d_%d.jpg", zoomFolder.getAbsolutePath(), x, y);
        try {
            writeImage(subImage, new File(name));
        } catch (IOException e) {
            e.printStackTrace();
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
        FileImageOutputStream output = new FileImageOutputStream(file);
        writer.setOutput(output);
        writer.write(null, new IIOImage(image, null, null), jpegParams);
        output.close();
    }
}
