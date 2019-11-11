package com.rs.tool.chipannotation;

import javax.imageio.*;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class Cut {

    public static final int TILE_SIZE = 512;
    public static final int BLOCK_LEVEL = 5;
    public static final int BLOCK_SIZE = TILE_SIZE << BLOCK_LEVEL;

    private static ImageReader getReader(File file) {
        try {
            ImageInputStream stream = ImageIO.createImageInputStream(file);

            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (readers.hasNext()) {
                try {
                    ImageReader reader = readers.next();
                    reader.setInput(stream, true, true);
                    return reader;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ImageContent readImage(File file) {
        ImageReader reader = getReader(file);
        if (reader != null) {
            try {
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());
                System.out.println("width: " + width + " height: " + height);
                return preProcess(width, height);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static ImageBlock readBlock(ImageReader imageReader, ImageContent imageContent, int blockX, int blockY) {
        return new ImageBlock(imageReader, imageContent.width, imageContent.height, TILE_SIZE, BLOCK_SIZE, blockX, blockY);
    }

    public static void processRegion(ImageContent imageContent, ImageBlock imageBlock, String imageFolder, ProgressCallback progressCallback) {
        for (ImageContent.Level level : imageContent.levels) {
            imageBlock.process(imageFolder, level, progressCallback);
        }
    }

    public static ImageContent preProcess(int width, int height) {
        ImageContent imageContent = new ImageContent();
        imageContent.tileSize = TILE_SIZE;
        imageContent.width = width;
        imageContent.height = height;
        imageContent.levels = new ArrayList<>();

        int currentLevel = 0;
        int currentWidth = imageContent.width;
        int currentHeight = imageContent.height;
        while ((currentWidth > imageContent.tileSize / 2 || currentHeight > imageContent.tileSize / 2) && currentLevel <= BLOCK_LEVEL) {
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
        void doneCut(int level, int x, int y);

        void alldone();

        void show(String message);

        void failed(String reason);
    }

    public static void cutAll(ImageContent imageContent, File file, File targetFolder, ProgressCallback progressCallback) {
        for (int i = 0; i <= imageContent.maxLevel; i++) {
            System.gc();
            File zoomFolder = new File(targetFolder.getAbsolutePath() + "/" + imageContent.name + "/" + i);
            boolean success = (zoomFolder.exists() && zoomFolder.isDirectory()) || zoomFolder.mkdirs();
            if (!success) {
                progressCallback.failed("cannot create zoom folder");
                return;
            }
        }

        int blockCountX = (imageContent.width - 1) / BLOCK_SIZE + 1;
        int blockCountY = (imageContent.height - 1) / BLOCK_SIZE + 1;
        int blockCount = blockCountX * blockCountY;
        int i = 0;
        ImageReader reader = getReader(file);
        for (int x = 0; x < blockCountX; x++) {
            for (int y = 0; y < blockCountY; y++) {
                i++;
                progressCallback.show("reading block " + i + "/" + blockCount + ", will take some time");
                ImageBlock imageBlock = readBlock(reader, imageContent, x, y);
                progressCallback.show("writing block " + i + "/" + blockCount);
                processRegion(imageContent, imageBlock, targetFolder.getAbsolutePath() + "/" + imageContent.name, progressCallback);
            }
        }

        progressCallback.alldone();
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
