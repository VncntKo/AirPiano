/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.airpiano.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.SoundPool;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Pair;
import android.util.TypedValue;

import org.tensorflow.lite.examples.airpiano.env.BorderedText;
import org.tensorflow.lite.examples.airpiano.env.ImageUtils;
import org.tensorflow.lite.examples.airpiano.env.Logger;
import org.tensorflow.lite.examples.airpiano.tflite.Classifier.Recognition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * A tracker that handles non-max suppression and matches existing objects to new detections.
 */
public class MultiBoxTracker {
    private static final float TEXT_SIZE_DIP = 18;
    private static final float MIN_SIZE = 16.0f;
    private static final int[] COLORS = {
            Color.parseColor("#F4924F")
//            10
    };
    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
    private final Logger logger = new Logger();
    private final Queue<Integer> availableColors = new LinkedList<Integer>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
    private final Paint boxPaint = new Paint();
    private final Paint inBox = new Paint();
    private final Paint outBox = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    private Matrix frameToCanvasMatrix;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;

    private int numDetect;
    private float lastX;
    private float lastY;
    private boolean first = true;
    private int count = 0;
    private int endCount = 0;
    private boolean flag = false;
    private boolean pressflag = false;  // 키보드를 눌렀을 때 한번만 소리나도록 설정하는 flag

    private boolean [] noteflag = {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false};


    public Path mPath = new Path();
    public static Bitmap bmap;
    public Bitmap pianoKeyboard = null;
    public Bitmap gray = null;
    SoundPool spool;
    private int[] sevenNote;

    public HashMap<Integer, Integer> getLine = new HashMap<Integer, Integer>();

    final static String foldername = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow/";

    public MultiBoxTracker(final Context context, int[] note, SoundPool pool) {
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark));
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(45.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);

        sevenNote = note;
        spool = pool;
    }

    public synchronized void setFrameConfiguration(
            final int width, final int height, final int sensorOrientation) {
        frameWidth = width;
        frameHeight = height;
        this.sensorOrientation = sensorOrientation;
    }

    protected void writeWord(String destination, String text, boolean append) {
        WriteTextFile(foldername, destination, text, append);
    }

    protected void WriteTextFile(String folderName, String fileName, String contents, boolean append) {
        try {
            File dir = new File(folderName);
            //디렉토리 폴더가 없으면 생성함
            if (!dir.exists()) {
                dir.mkdir();
            }
            //파일 output stream 생성
            FileOutputStream fos = new FileOutputStream(folderName + "/" + fileName, append);
            //파일쓰기
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
            writer.write(contents);
            writer.flush();

            writer.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void drawDebug(final Canvas canvas) {
        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Style.STROKE);


        for (final Pair<Float, RectF> detection : screenRects) {
            final RectF rect = detection.second;

            canvas.drawRect(rect.left, 1100 - rect.top, rect.right, 1100 - rect.bottom, boxPaint);
        }
    }

    public synchronized int trackResults(final List<Recognition> results, final long timestamp) {
//        logger.i("Processing %d results from %d", results.size(), timestamp);
        numDetect = results.size();

        processResults(results);

//        return 1;
        return numDetect;
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public int sum(List<Integer> list) {
        int sum = 0;

        for (int i : list) {
            sum = sum + i;
        }

        return sum;
    }

    public synchronized void draw(final Canvas canvas, final Bitmap bit, final Bitmap graybit, final ArrayList[] notequeue) {
        pianoKeyboard = bit;
        gray = graybit;

        float cwidth = canvas.getWidth();
<<<<<<< HEAD
//        float cheight = canvas.getHeight();
        float keywidth = cwidth/(float)16.0;
=======
        float cheight = canvas.getHeight();
        float keywidth = cwidth / (float) 16.0;
>>>>>>> 07e45f322937b62c19f9d0b97370f25b5ad691a5

        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);

//        logger.i("trackedObjects count test : " + trackedObjects.size());

        bmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas C = new Canvas(bmap);
        Paint P = new Paint();
        float canvasheight = C.getHeight();

        P.setAlpha(155);
        canvas.drawBitmap(pianoKeyboard, null, new Rect(0, 127, C.getWidth(), (int) canvasheight), P);
        P.setColor(Color.RED);
        P.setStrokeWidth(20);
        canvas.drawLine(0, canvasheight / 6 * 5, C.getWidth(), canvasheight / 6 * 5, P);

        ArrayList<Integer> fornow = new ArrayList<>();

        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos = new RectF(recognition.location);

            getFrameToCanvasMatrix().mapRect(trackedPos);

            if (1200 - trackedPos.centerY() > canvasheight / 6 * 5) {
                inBox.setColor(Color.BLUE);
                inBox.setStrokeWidth(25);
                canvas.drawCircle(trackedPos.centerX() - 20, 1200 - trackedPos.centerY(), 25, inBox);
                int now = (int) ((trackedPos.centerX() - 20) / keywidth);

                if (!noteflag[now]) {
                    spool.play(now, 1, 1, 0, 0, 1);
                    noteflag[now] = true;
                }
                fornow.add(now);
            } else {
                outBox.setColor(Color.RED);
                outBox.setStrokeWidth(25);
                canvas.drawCircle(trackedPos.centerX() - 20, 1200 - trackedPos.centerY(), 25, outBox);
            }
        }

//        logger.i("this is list fornow : " + fornow);
        for (int i=1; i<notequeue.length; i++){
            if (fornow.contains(i)){

                notequeue[i].remove(0);
                notequeue[i].add(1);
            } else {
                notequeue[i].remove(0);
                notequeue[i].add(0);
            }
        }

<<<<<<< HEAD
        P.setAlpha(70);
        for (int i=0; i<noteflag.length; i++){
            if (sum(notequeue[i]) == 0){
=======
        for (int i = 0; i < noteflag.length; i++) {
            if (sum(notequeue[i]) == 0) {
>>>>>>> 07e45f322937b62c19f9d0b97370f25b5ad691a5
                noteflag[i] = false;
            }
            else{
                canvas.drawBitmap(gray, null, new Rect((int) keywidth * i, 127, (int) keywidth * (i+1), (int) canvasheight), P);
            }
        }
    }

    public synchronized void getLineClear() {
        getLine.clear();
    }

    public synchronized Integer getY(final Canvas canvas) {
        if (!flag) {
            mPath.reset();
            first = true;

            int big = 0;
            int bigkey = -1;
            Iterator entries = getLine.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry entry = (Map.Entry) entries.next();
                Integer key = (Integer) entry.getKey();
                Integer value = (Integer) entry.getValue();
                if (big < value) {
                    big = value;
                    bigkey = key;
                }
            }

            return bigkey;
        }

        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);

        if (count > 3 && flag) {
//            for (final TrackedRecognition recognition : trackedObjects) {
            for (final Pair<Float, RectF> recognition : screenRects) {

                final RectF trackedPos = new RectF(recognition.second);

                getFrameToCanvasMatrix().mapRect(trackedPos);
                boxPaint.setColor(Color.RED);

                float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;

                int thisY = (int) trackedPos.centerY();
                thisY = 1200 - thisY;
//                thisY = (int) thisY / 3;
                logger.i("this is thisY !!! : " + thisY);

                if (getLine.containsKey(thisY)) {
                    int oldValue = getLine.get(thisY);
                    getLine.put(thisY, oldValue + 1);
                } else {
                    getLine.put(thisY, 1);
                }

                if (first) {
                    mPath.moveTo(trackedPos.centerX() - 20, 1200 - trackedPos.centerY());
                    first = false;

                } else {
                    mPath.lineTo(trackedPos.centerX() - 20, 1200 - trackedPos.centerY());
                }
                canvas.drawPath(mPath, boxPaint);
                bmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas C = new Canvas(bmap);
                Paint P = new Paint();
                C.drawColor(Color.WHITE);
                P.setColor(Color.BLACK);
//                P.setAlpha(200);
                P.setStyle(Style.STROKE);
                P.setStrokeWidth(45.0f);        //두께 width
                P.setStrokeCap(Cap.ROUND);
                P.setStrokeJoin(Join.ROUND);
                P.setStrokeMiter(100);
                C.drawPath(mPath, P);
                lastX = trackedPos.centerX();
                lastY = trackedPos.centerY();
                break;
            }
        }
        return -22;
    }

    private synchronized void drawHit(int a, int b, int c, int d) {
        final Canvas canvas = new Canvas();
        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStrokeWidth(20f);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Style.STROKE);

        while (true) {
            canvas.drawRect(a, b, c, d, boxPaint);
        }
    }

    private void processResults(final List<Recognition> results) {
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            logger.v(
                    "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! " + detectionFrameRect);
                continue;
            }

            rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
        }

        if (rectsToTrack.isEmpty()) {
            logger.i("Nothing to track, aborting.");
            return;
        }

        trackedObjects.clear();
        for (final Pair<Float, Recognition> potential : rectsToTrack) {
            final TrackedRecognition trackedRecognition = new TrackedRecognition();
            trackedRecognition.detectionConfidence = potential.first;
            trackedRecognition.location = new RectF(potential.second.getLocation());
            trackedRecognition.title = potential.second.getTitle();
//            trackedRecognition.color = COLORS[trackedObjects.size()];
            trackedRecognition.color = COLORS[0];
            trackedObjects.add(trackedRecognition);

            if (trackedObjects.size() >= 10) {
                break;
            }
        }
    }

    private static class TrackedRecognition {
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }
}