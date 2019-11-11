package com.rs.tool.chipannotation;

import org.imgscalr.Scalr;

import javax.imageio.*;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageBlock {

    private int tileSize;
    private final int blockSize;
    private final int blockX;
    private final int blockY;
    private BufferedImage image;

    public ImageBlock(ImageReader reader, int imageWidth, int imageHeight, int tileSize, int blockSize, int blockX, int blockY) {
        this.tileSize = tileSize;
        this.blockSize = blockSize;
        this.blockX = blockX;
        this.blockY = blockY;

        int left = blockSize * blockX;
        int top = blockSize * blockY;
        int right = Math.min(imageWidth, left + blockSize);
        int bottom = Math.min(imageHeight, top + blockSize);
        Rectangle sourceRegion = new Rectangle(left, top, right - left, bottom - top);

        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(sourceRegion); // Set region

        try {
            image = reader.read(0, param);
        } catch (IOException e) {
            e.printStackTrace();
        }

        BufferedImage resized = new BufferedImage(blockSize, blockSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        image = resized;
    }

    public void process(String targetFolder, ImageContent.Level level, Cut.ProgressCallback progressCallback) {
        BufferedImage resized = resize(level.level);

        int tileRatio = blockSize / tileSize;
        int count = tileRatio >> level.level;
        int tileX1 = blockX * count;
        int tileY1 = blockY * count;
        int tileX2 = Math.min(tileX1 + count - 1, level.xMax - 1);
        int tileY2 = Math.min(tileY1 + count - 1, level.yMax - 1);

        for (int x = tileX1; x <= tileX2; x++) {
            for (int y = tileY1; y <= tileY2; y++) {
                BufferedImage subImage = resized.getSubimage((x - tileX1) * tileSize, (y - tileY1) * tileSize, tileSize, tileSize);
                System.out.printf("z=%d, (%d,%d)\n", level.level, x, y);
                progressCallback.doneCut(level.level, x, y);
                try {
                    writeImage(subImage, new File(String.format("%s/%d/%d_%d.jpg", targetFolder, level.level, x, y)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private BufferedImage resize(int level) {
        if (level == 0) {
            return image;
        } else {
            return Scalr.resize(image, Scalr.Method.QUALITY, image.getWidth() >> level, image.getHeight() >> level);
        }
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
