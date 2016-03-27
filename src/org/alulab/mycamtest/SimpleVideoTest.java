/*
 * Copyright (C) 2016 Sudipto Chandra.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.alulab.mycamtest;

import java.awt.Color;
import java.io.File;
import java.util.Vector;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import marvin.MarvinDefinitions;
import marvin.gui.MarvinImagePanel;
import marvin.image.MarvinImage;
import marvin.image.MarvinImageMask;
import marvin.io.MarvinImageIO;
import marvin.plugin.MarvinImagePlugin;
import marvin.util.MarvinAttributes;
import marvin.util.MarvinPluginLoader;
import marvin.video.MarvinJavaCVAdapter;
import marvin.video.MarvinVideoInterface;
import marvin.video.MarvinVideoInterfaceException;

/**
 *
 * @author Dipu
 */
public class SimpleVideoTest implements Runnable {

    static {
        MarvinDefinitions.setImagePluginPath("./res/marvin/plugins/image/");
    }

    private Thread mThread;
    private MarvinImage currentImage;
    private volatile boolean processing;
    private final MarvinImagePanel mVideoPanel;
    private final MarvinVideoInterface mVideoAdapter;

    public SimpleVideoTest(MarvinImagePanel videoPanel) {
        mVideoPanel = videoPanel;
        mVideoAdapter = new MarvinJavaCVAdapter();

        try {
            // Create the VideoAdapter and connect to the camera
            mVideoAdapter.connect(0);
        } catch (MarvinVideoInterfaceException ex) {
        }
    }

    public void startProcessing() {
        if (!processing) {
            // Start the thread for requesting the video frames
            mThread = new Thread(this);
            mThread.start();
            processing = true;
        }
    }

    public void stopProcessing() {
        processing = false;
        while (mThread != null && mThread.isAlive()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
            }
        }
    }

    public void saveImage(File file) {
        if (currentImage != null) {
            MarvinImageIO.saveImage(currentImage, file.toString());
        }
    }

    @Override
    public void run() {
        while (processing) {
            try {
                // Request a video frame and set into the VideoPanel
                MarvinImage imgIn = mVideoAdapter.getFrame();
                currentImage = processImage(imgIn);
                mVideoPanel.setImage(currentImage);
            } catch (MarvinVideoInterfaceException ex) {
            }
        }
    }

    //
    // use plugins to process images
    //
    MarvinImagePlugin grayPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.color.grayScale.jar");
    MarvinImagePlugin sepiaPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.color.sepia.jar");
    MarvinImagePlugin invertPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.color.invert.jar");
    MarvinImagePlugin edgeDetectPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.edge.edgeDetector.jar");
    MarvinImagePlugin tvPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.artistic.television.jar");
    MarvinImagePlugin mosaicPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.artistic.mosaic.jar");
    MarvinImagePlugin pluginMotionRegions = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.difference.differentRegions.jar");
    MarvinImagePlugin gaussianPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.blur.gaussianBlur.jar");
    MarvinImagePlugin pixelizePlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.blur.pixelize.jar");
    MarvinImagePlugin backgroundPlugin = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.background.determineFixedCameraBackground.jar");

    public BooleanProperty useGrayScale = new SimpleBooleanProperty(false);
    public BooleanProperty useEdgeDetector = new SimpleBooleanProperty(false);
    public BooleanProperty useInvert = new SimpleBooleanProperty(false);
    public BooleanProperty useSepia = new SimpleBooleanProperty(false);
    public BooleanProperty useGaussianBlur = new SimpleBooleanProperty(false);
    public BooleanProperty usePixelize = new SimpleBooleanProperty(false);
    public BooleanProperty useTV = new SimpleBooleanProperty(false);
    public BooleanProperty useMosaic = new SimpleBooleanProperty(false);
    public BooleanProperty useCompareRegion = new SimpleBooleanProperty(false);
    public BooleanProperty useMyOwn = new SimpleBooleanProperty(false);
    public BooleanProperty useBackground = new SimpleBooleanProperty(false);

    public long SAMPLES = 0;

    private MarvinImage processImage(MarvinImage imgIn) {
        ++SAMPLES;
        MarvinImage img = imgIn.clone();

        if (useGrayScale.get()) {
            grayPlugin.process(img, img);
        }
        if (useSepia.get()) {
            sepiaPlugin.process(img, img);
        }
        if (useInvert.get()) {
            invertPlugin.process(img, img);
        }
        if (useEdgeDetector.get()) {
            edgeDetectPlugin.process(img, img);
        }
        if (useTV.get()) {
            tvPlugin.process(img, img);
        }
        if (useGaussianBlur.get()) {
            gaussianPlugin.process(img, img);
        }
        if (usePixelize.get()) {
            pixelizePlugin.process(img, img);
        }
        if (useMosaic.get()) {
            mosaicPlugin.process(img, img);
        }
        if (useMyOwn.get()) {
            img = MyOwnProcess(img);
        }
        if (useBackground.get()) {
            backgroundPlugin.process(img, img);
        } else if (useCompareRegion.get()) {
            img = compareRegions(img);
        }
        return img;

    }

    MarvinImage lastImage = null;

    private MarvinImage compareRegions(MarvinImage img) {

        final int sensibility = 50; // the lower the higher

        if (lastImage == null) {
            lastImage = img;
            return img;
        }

        MarvinAttributes attributesOut = new MarvinAttributes(null);
        pluginMotionRegions.setAttribute("comparisonImage", lastImage);
        pluginMotionRegions.setAttribute("colorRange", sensibility);
        pluginMotionRegions.process(img, img, attributesOut, MarvinImageMask.NULL_MASK, false);

        for (int[] rect : (Vector<int[]>) attributesOut.get("regions")) {
            img.drawRect(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], Color.white);
        }

        return img;
    }


    {
        useMyOwn.addListener((ObservableValue<? extends Object> observable, Object oldValue, Object newValue) -> {
            averagePos = averageSIZ = 0;
        });
    }

    int MAX_SIZ = 7;
    int averagePos = 0, averageSIZ = 0;
    MarvinImage[] averageImage = new MarvinImage[100];

    int avg(double a, double b) {
        return (int) Math.floor(a * (averageSIZ - 1) + b) / averageSIZ;
    }
    
    private MarvinImage MyOwnProcess(MarvinImage img) {

        int w = img.getWidth();
        int h = img.getHeight();
        averageImage[averagePos] = img.clone();
        averageSIZ = Math.max(1 + averagePos, averageSIZ);
        averagePos = (1 + averagePos) % MAX_SIZ;
         
        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                int rsum, gsum, bsum, asum;
                rsum = gsum = bsum = asum = 0;
                for (int i = 0; i < averageSIZ; ++i) {
                    Color col = new Color(averageImage[i].getIntColor(x, y));
                    rsum += col.getRed();
                    gsum += col.getGreen();
                    bsum += col.getBlue();
                    asum += col.getAlpha();
                }
                int r = (int) Math.round((double) rsum / averageSIZ);
                int g = (int) Math.round((double) gsum / averageSIZ);
                int b = (int) Math.round((double) bsum / averageSIZ);
                int a = (int) Math.round((double) asum / averageSIZ);
                img.setIntColor(x, y, (new Color(r, g, b, a)).getRGB());
            }
        }

        return img;
    }

}
