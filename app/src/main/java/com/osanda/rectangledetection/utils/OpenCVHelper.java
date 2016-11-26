package com.osanda.rectangledetection.utils;

import android.graphics.Path;
import android.hardware.Camera;

import com.osanda.rectangledetection.MainActivity;
import com.osanda.rectangledetection.models.MatData;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import rx.Observable;

public class OpenCVHelper {

    private static final String TAG = OpenCVHelper.class.getSimpleName();
    public static MainActivity mainactivity;
    public static double screenHeight, screenWidth;

    public static enum Movement {LEFT, RIGHT, FORWARD, BACKWARD, CLOSER, AWAY, STOPPED}

    public static Movement move = Movement.STOPPED;
    public static long prevTime = -1;
    public static Observable<MatData> resize(MatData matData, float requestWidth, float requestHeight) {
        return Observable.create(sub -> {
            Mat mat = matData.oriMat;
            final int height = mat.height();
            final int width = mat.width();
            float ratioW = width / requestWidth;
            float ratioH = height / requestHeight;
            float scaleRatio = ratioW > ratioH ? ratioW : ratioH;
            Size size = new Size(mat.width() / scaleRatio, mat.height() / scaleRatio);
            // eranga begin
            screenHeight = size.height;
            screenWidth = size.width;
            // eranga end
            Mat resultMat = new Mat(size, mat.type());
            Imgproc.resize(mat, resultMat, size);
            //Log.v(TAG, "request size:" + requestWidth + "," + requestHeight +" ,scale to:" + resultMat.width() + "," + resultMat.height());
            matData.resizeMat = resultMat;
            sub.onNext(matData);
            sub.onCompleted();
        });
    }

    public static Observable<MatData> getRgbMat(MatData matData, byte[] data, Camera camera) {
        return Observable.create(sub -> {
            try {
                long now = System.currentTimeMillis();
                Camera.Size size = camera.getParameters().getPreviewSize();
                Mat mYuv = new Mat(size.height + size.height / 2, size.width, CvType.CV_8UC1);
                mYuv.put(0, 0, data);
                Mat mRGB = new Mat();
                Imgproc.cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3);
                Mat dst = new Mat();
                Core.flip(mRGB.t(), dst, 1);
                matData.oriMat = dst;
                //Log.v(TAG, "getRgbMat time:" + (System.currentTimeMillis() - now));
                sub.onNext(matData);
                sub.onCompleted();
            } catch (Exception e) {
                e.printStackTrace();
                sub.onCompleted();
            }
        });
    }

    public static Observable<MatData> getMonochromeMat(MatData matData) {
        return Observable.create(sub -> {
            long now = System.currentTimeMillis();
            Mat edgeMat = getEdge(matData.resizeMat);
            matData.monoChrome = new Mat();
            Imgproc.threshold(edgeMat, matData.monoChrome, 127, 255, Imgproc.THRESH_BINARY);
            //Log.v(TAG, "getMonochromeMat time:" + (System.currentTimeMillis() - now));
            sub.onNext(matData);
            sub.onCompleted();
        });
    }

    private static Mat getEdge(Mat oriMat) {
        long now = System.currentTimeMillis();
        Mat sobelX = new Mat();
        Mat sobelY = new Mat();
        Mat destination = new Mat(oriMat.rows(), oriMat.cols(), oriMat.type());
        Imgproc.cvtColor(oriMat, destination, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.Sobel(destination, sobelX, CvType.CV_16S, 1, 0);
        Imgproc.Sobel(destination, sobelY, CvType.CV_16S, 0, 1);
        Mat absX = new Mat();
        Mat absY = new Mat();
        Core.convertScaleAbs(sobelX, absX);
        Core.convertScaleAbs(sobelY, absY);
        Mat result = new Mat();
        Core.addWeighted(absX, 0.5, absY, 0.5, 0, result);
        //Log.v(TAG, "getEdge time:" + (System.currentTimeMillis() - now));
        return result;
    }

    public static Observable<MatData> getContoursMat(MatData matData) {
        return Observable.create(sub -> {
            long now = System.currentTimeMillis();
            ArrayList<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(matData.monoChrome.clone(), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            final int width = matData.monoChrome.rows();
            final int height = matData.monoChrome.cols();
            int matArea = width * height;
            for (int i = 0; i < contours.size(); i++) {
                double contoursArea = Imgproc.contourArea(contours.get(i));
                MatOfPoint2f approx = new MatOfPoint2f();
                MatOfPoint2f contour = new MatOfPoint2f(contours.get(i).toArray());
                double epsilon = Imgproc.arcLength(contour, true) * 0.1;
                Imgproc.approxPolyDP(contour, approx, epsilon, true);
                if (Math.abs(contoursArea) < matArea * 0.01 ||
                        !Imgproc.isContourConvex(new MatOfPoint(approx.toArray()))) {
                    continue;
                }
                Imgproc.drawContours(matData.resizeMat, contours, i, new Scalar(0, 255, 0));

                List<Point> points = approx.toList();
                int pointCount = points.size();
                double threshold = 0.3;

                //if(prevTime == -1 || prevTime + 1000 < System.currentTimeMillis()) {
                if (pointCount == 4 && screenWidth * screenHeight != 0) {
                    double lbWidth = screenWidth * threshold / 2;
                    double ubWidth = screenWidth * (1 - threshold / 2);
                    double lbHeight = screenHeight * threshold / 2;
                    double ubHeight = screenHeight * (1 - threshold / 2);
                    ArrayList<Double> xCoordinates = new ArrayList<>();
                    xCoordinates.add(points.get(0).x);
                    xCoordinates.add(points.get(1).x);
                    xCoordinates.add(points.get(2).x);
                    xCoordinates.add(points.get(3).x);
                    ArrayList<Double> yCoordinates = new ArrayList<>();
                    yCoordinates.add(points.get(0).y);
                    yCoordinates.add(points.get(1).y);
                    yCoordinates.add(points.get(2).y);
                    yCoordinates.add(points.get(3).y);
                    double minX = Collections.min(xCoordinates);
                    double maxX = Collections.max(xCoordinates);
                    double minY = Collections.min(yCoordinates);
                    double maxY = Collections.max(yCoordinates);
                    if (maxX - minX > screenWidth * (1 - threshold) && maxY - minY > screenHeight * (1 - threshold)) {
                        if (!move.equals(Movement.STOPPED)) {
                            System.out.println("Device is in perfect position. Keep it stable");
                            move = Movement.STOPPED;
                            prevTime = System.currentTimeMillis();
                            mainactivity.speakOut("Detected");
                        }
                    } else {
                        if (maxX - minX < screenWidth * (1 - threshold) && maxY - minY < screenHeight * (1 - threshold)) {
                            if (!move.equals(Movement.CLOSER)) {
                                System.out.println("Slowly move the device closer");
                                move = Movement.CLOSER;
                                prevTime = System.currentTimeMillis();
                                mainactivity.speakOut("Move closer");
                            }
                        }
                        if (minX < lbWidth) {
                            if (!move.equals(Movement.BACKWARD)) {
                                System.out.println("Slowly move the device backward");
                                move = Movement.BACKWARD;
                                prevTime = System.currentTimeMillis();
                                mainactivity.speakOut("Move backward");
                            }
                        }
                        if (maxX > ubWidth) {
                            if (!move.equals(Movement.FORWARD)) {
                                System.out.println("Slowly move the device forward");
                                move = Movement.FORWARD;
                                prevTime = System.currentTimeMillis();
                                mainactivity.speakOut("Move forward");
                            }
                        }
                        if (minY < lbHeight) {
                            if (!move.equals(Movement.LEFT)) {
                                System.out.println("Slowly move the device to the left side");
                                move = Movement.LEFT;
                                prevTime = System.currentTimeMillis();
                                mainactivity.speakOut("Move left");
                            }
                        }
                        if (maxY > ubHeight) {
                            if (!move.equals(Movement.RIGHT)) {
                                System.out.println("Slowly move the device to the right side");
                                move = Movement.RIGHT;
                                prevTime = System.currentTimeMillis();
                                mainactivity.speakOut("Move right");
                            }
                        }
                    }
                } else if (pointCount != 4) {
                    if (!move.equals(Movement.AWAY)) {
                        System.out.println("Slowly move the device away from the material");
                        move = Movement.AWAY;
                        prevTime = System.currentTimeMillis();
                        mainactivity.speakOut("Move away");
                    }
                }

                //}else{
                //    prevTime = System.currentTimeMillis();
                //}

                LinkedList<Double> cos = new LinkedList<>();
                for (int j = 2; j < pointCount + 1; j++) {
                    cos.addLast(angle(points.get(j % pointCount), points.get(j - 2), points.get(j - 1)));
                }
                Collections.sort(cos, (lhs, rhs) -> lhs.intValue() - rhs.intValue());
                double mincos = cos.getFirst();
                double maxcos = cos.getLast();
                if (points.size() == 4 && mincos >= -0.3 && maxcos <= 0.5) {
                    for (int j = 0; j < points.size(); j++) {
                        Core.circle(matData.resizeMat, points.get(j), 6, new Scalar(255, 0, 0), 6);
                    }
                    matData.points = points;
                    break;
                }

            }
            //Log.v(TAG, "getContoursMat time:" + (System.currentTimeMillis() - now));
            sub.onNext(matData);
            sub.onCompleted();
        });
    }

    private static double angle(Point pt1, Point pt2, Point pt0) {
        double dx1 = pt1.x - pt0.x;
        double dy1 = pt1.y - pt0.y;
        double dx2 = pt2.x - pt0.x;
        double dy2 = pt2.y - pt0.y;
        //System.out.println("@@@@@@@@@@@@@@@@@@@@@@: 0: " + pt0.x + ", " + pt0.x + "\t1: " + pt1.x + ", " + pt1.x + "\t2: " + pt2.x + ", " + pt2.x);
        //System.out.println("DSA" + (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10));
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    public static Observable<MatData> getPath(MatData matData) {
        return Observable.create(subscriber -> {
            List<Point> cameraPoints = new ArrayList<>();
            if (matData.points != null && matData.points.size() == 4) {
                for (int i = 0; i < matData.points.size(); i++) {
                    Point point = matData.points.get(i);
                    Point cameraPoint = new Point(
                            point.x * matData.resizeRatio * matData.cameraRatio,
                            point.y * matData.resizeRatio * matData.cameraRatio);
                    cameraPoints.add(cameraPoint);
                }
                Collections.sort(cameraPoints, (lhs, rhs) ->
                        OpenCVHelper.getDistance(lhs) - OpenCVHelper.getDistance(rhs));
                Path path = new Path();
                path.moveTo((float) cameraPoints.get(0).x,
                        (float) cameraPoints.get(0).y);
                path.lineTo((float) cameraPoints.get(1).x,
                        (float) cameraPoints.get(1).y);
                path.lineTo((float) cameraPoints.get(3).x,
                        (float) cameraPoints.get(3).y);
                path.lineTo((float) cameraPoints.get(2).x,
                        (float) cameraPoints.get(2).y);
                path.lineTo((float) cameraPoints.get(0).x,
                        (float) cameraPoints.get(0).y);
                matData.cameraPath = path;
            }
            subscriber.onNext(matData);
            subscriber.onCompleted();
        });
    }

    public static int getDistance(Point point) {
        double x1 = 0;
        double x2 = point.x;
        double y1 = 0;
        double y2 = point.y;
        return (int) Math.sqrt(
                Math.pow(x1 - x2, 2) +
                        Math.pow(y1 - y2, 2));
    }
}
