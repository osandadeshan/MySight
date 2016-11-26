package com.osanda.rectangledetection;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.osanda.R;
import com.osanda.ocr.CaptureActivity;
import com.osanda.rectangledetection.models.CameraData;
import com.osanda.rectangledetection.models.MatData;
import com.osanda.rectangledetection.utils.OpenCVHelper;
import com.osanda.rectangledetection.views.CameraPreview;
import com.osanda.rectangledetection.views.DrawView;

import org.opencv.android.OpenCVLoader;

import java.util.Locale;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private static final String TAG = MainActivity.class.getSimpleName();

    static {
        if (!OpenCVLoader.initDebug()) {
            //Log.v(TAG, "init OpenCV");
        }
    }

    private PublishSubject<CameraData> subject = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OpenCVHelper.mainactivity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tts = new TextToSpeech(this, this);
        CameraPreview cameraPreview = (CameraPreview) findViewById(R.id.camera_preview);
        cameraPreview.setCallback((data, camera) -> {
            CameraData cameraData = new CameraData();
            cameraData.data = data;
            cameraData.camera = camera;
            subject.onNext(cameraData);
        });
        cameraPreview.setOnClickListener(v -> cameraPreview.focus());
        DrawView drawView = (DrawView) findViewById(R.id.draw_layout);
        subject.concatMap(cameraData ->
                OpenCVHelper.getRgbMat(new MatData(), cameraData.data, cameraData.camera))
                .concatMap(matData -> OpenCVHelper.resize(matData, 400, 400))
                .map(matData -> {
                    matData.resizeRatio = (float) matData.oriMat.height() / matData.resizeMat.height();
                    matData.cameraRatio = (float) cameraPreview.getHeight() / matData.oriMat.height();
                    return matData;
                })
                .concatMap(this::detectRect)
                .compose(mainAsync())
                .subscribe(matData -> {

                    if (drawView != null) {
                        if (matData.cameraPath != null) {
                            drawView.setPath(matData.cameraPath);
                            ;
                        } else {
                            drawView.setPath(null);
                        }
                        drawView.invalidate();

                    }

                });

//        Intent myIntent = new Intent(MainActivity.this, com.osanda.ocr.CaptureActivity.class);
//        finish();
//        startActivity(myIntent)

    }

    private Observable<MatData> detectRect(MatData mataData) {
        return Observable.just(mataData)
                .concatMap(OpenCVHelper::getMonochromeMat)
                .concatMap(OpenCVHelper::getContoursMat)
                .concatMap(OpenCVHelper::getPath);
    }

    private static <T> Observable.Transformer<T, T> mainAsync() {
        return obs -> obs.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.UK);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_LONG).show();
                Log.e("TTS", "Language is not supported");
            }
        } else {
            Log.e("TTS", "Initilization Failed");
        }

    }

    public void speakOut(String text) {

        //String text = txtText.getText().toString();
        if (text.length() == 0) {
            tts.speak("You haven't typed text", TextToSpeech.QUEUE_FLUSH, null);

        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            //tts.speak("You haven't typed text", TextToSpeech.QUEUE_ADD, null);
        }
        if (text == "Detected") {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.finish();
            Intent myIntent = new Intent(MainActivity.this, CaptureActivity.class);
            MainActivity.this.startActivity(myIntent);

        }

//        try {
//            Thread.sleep(text.length()*100);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

    }

}
