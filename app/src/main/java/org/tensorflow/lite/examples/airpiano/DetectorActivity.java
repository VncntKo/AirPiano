/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.airpiano;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.SoundPool;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import org.tensorflow.lite.examples.airpiano.customview.OverlayView;
import org.tensorflow.lite.examples.airpiano.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.airpiano.env.BorderedText;
import org.tensorflow.lite.examples.airpiano.env.ImageUtils;
import org.tensorflow.lite.examples.airpiano.env.Logger;
import org.tensorflow.lite.examples.airpiano.tflite.Classifier;
import org.tensorflow.lite.examples.airpiano.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.airpiano.tracking.MultiBoxTracker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.tensorflow.lite.examples.airpiano.tracking.MultiBoxTracker.bmap;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

//   Configuration values for the prepackaged SSD model.
//  private static final int TF_OD_API_INPUT_SIZE = 300;
//  private static final boolean TF_OD_API_IS_QUANTIZED = true;
//  private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
//  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    // Configuration values for the prepackaged yolo model
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "airpiano.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/fingertip_label.txt";

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(760, 400);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
//    public Bitmap pianoKeyboard = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    private int QueueSize = 6;

    SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
    private long mNow;
    private Date mDate;

    final static String foldername = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow/";
    final static String filename = "inferenceTimeLog.txt";

    private boolean offInit = true;
    private int standardY = -1;

    private ArrayList<Integer> inside1 = new ArrayList<>();
    private ArrayList<Integer> inside2 = new ArrayList<>();
    private ArrayList<Integer> inside3 = new ArrayList<>();
    private ArrayList<Integer> inside4 = new ArrayList<>();
    private ArrayList<Integer> inside5 = new ArrayList<>();
    private ArrayList<Integer> inside6 = new ArrayList<>();
    private ArrayList<Integer> inside7 = new ArrayList<>();
    private ArrayList<Integer> inside8 = new ArrayList<>();
    private ArrayList<Integer> inside9 = new ArrayList<>();
    private ArrayList<Integer> inside10 = new ArrayList<>();
    private ArrayList<Integer> inside11 = new ArrayList<>();
    private ArrayList<Integer> inside12 = new ArrayList<>();
    private ArrayList<Integer> inside13 = new ArrayList<>();
    private ArrayList<Integer> inside14 = new ArrayList<>();
    private ArrayList<Integer> inside15 = new ArrayList<>();

    @Override
    public void onClick(View v) {

    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        SoundPool pool;
        pool = new SoundPool(5, AudioManager.STREAM_MUSIC, 3);
        int C3 = pool.load(this, R.raw.pianomfc3, 1);
        int D3 = pool.load(this, R.raw.pianomfd3, 1);
        int E3 = pool.load(this, R.raw.pianomfe3, 1);
        int F3 = pool.load(this, R.raw.pianomff3, 1);
        int G3 = pool.load(this, R.raw.pianomfg3, 1);
        int A3 = pool.load(this, R.raw.pianomfa3, 1);
        int B3 = pool.load(this, R.raw.pianomfb3, 1);
        int C4 = pool.load(this, R.raw.pianomfc4, 1);
        int D4 = pool.load(this, R.raw.pianomfd4, 1);
        int E4 = pool.load(this, R.raw.pianomfe4, 1);
        int F4 = pool.load(this, R.raw.pianomff4, 1);
        int G4 = pool.load(this, R.raw.pianomfg4, 1);
        int A4 = pool.load(this, R.raw.pianomfa4, 1);
        int B4 = pool.load(this, R.raw.pianomfb4, 1);

        for(int i=0;i<QueueSize;i++){
            inside1.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside2.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside3.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside4.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside5.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside6.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside7.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside8.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside9.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside10.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside11.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside12.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside13.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside14.add(0);
        }
        for(int i=0;i<QueueSize;i++){
            inside15.add(0);
        }

        ArrayList [] notequeue = {inside1, inside2, inside3, inside4, inside5,
                inside6, inside7, inside8, inside9, inside10,
                inside11, inside12, inside13, inside14, inside15};

        int[] note = {C3, D3, E3, F3, G3, A3, B3, C4, D4, E4, F4, G4, A4, B4};

        tracker = new MultiBoxTracker(this, note, pool);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);



        Resources res = getResources();
        BitmapDrawable bd = null;
        BitmapDrawable gray = null;
        bd = (BitmapDrawable) res.getDrawable(R.drawable.piano, null);
        gray = (BitmapDrawable) res.getDrawable(R.drawable.gray, null);
        bd.setAlpha(50);
        gray.setAlpha(10);
        Bitmap bit = bd.getBitmap();
        Bitmap graybit = gray.getBitmap();

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        boolean first = true;
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas, bit, graybit, notequeue);
                        if (isDebug()) {
                            tracker.draw(canvas, null, null, notequeue);
                        }
                        if (isInit()) {
                            tracker.getY(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
//        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();


        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
//                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        Resources res = getResources();
                        BitmapDrawable bd = null;
                        bd = (BitmapDrawable) res.getDrawable(R.drawable.piano, null);
                        Bitmap bit = bd.getBitmap();

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        canvas.drawBitmap(bit, 0, 0, null); // 피아노 건반

                        mNow = System.currentTimeMillis();
                        mDate = new Date(mNow);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);

                        if (isInit()) {
                            standardY = tracker.getY(canvas);
                            tracker.getLineClear();
                            InitClick = false;

                            turnOffInit();
//                                canvas.drawRect(0, standardY+5, 700, standardY-5, paint);
                        }
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }


    protected boolean turnOffInit() {
        return true;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
// checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}
