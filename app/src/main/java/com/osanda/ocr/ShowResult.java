package com.osanda.ocr;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.osanda.R;

import java.util.Locale;

public class ShowResult extends Activity implements TextToSpeech.OnInitListener {

    String tem;
    private TextToSpeech tts;
    private static final String BASE_URL = "file:///android_asset/html/";
    public static final String ABOUT_PAGE = "about.html";
    public static final String HELP_PAGE = "help.html";
    private final Button.OnClickListener doneListener = new Button.OnClickListener() {
        @Override
        public void onClick(View view) {
            finish();
        }
    };
    private WebView webView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_result);
        tts = new TextToSpeech(this, this);
        webView = (WebView) findViewById(R.id.help_contents);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            tem = extras.getString("resultText");

        }

        ActionBar actionBar = getActionBar();
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);


    }

    /**
     * On selecting action bar icons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        TextView ocrResultTextView = (TextView) findViewById(R.id.resultText);
        // Take appropriate action for each action item click
        switch (item.getItemId()) {

            case R.id.action_copy:
                // copy
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Text copied to clipboard", ocrResultTextView.getText());
                Toast.makeText(getApplicationContext(), "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                clipboard.setPrimaryClip(clip);
                return true;

            case R.id.action_speak:
                // read
                speakOut(ocrResultTextView);
                return true;


            case R.id.action_share:
                // share
                String temShare = String.valueOf(ocrResultTextView.getText());

                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, temShare);
                startActivity(Intent.createChooser(sharingIntent, "Share via"));
                return true;


            case R.id.action_clear:
                // clear
                ocrResultTextView.setText("");
                return true;

            case R.id.action_help:
                // help
                webView.loadUrl(BASE_URL + HELP_PAGE);

                // Show an OK button.
                View doneButton1 = findViewById(R.id.done_button);
                doneButton1.setOnClickListener(doneListener);

                doneButton1.setVisibility(View.VISIBLE);
                return true;

            case R.id.action_about:
                // about
                webView.loadUrl(BASE_URL + ABOUT_PAGE);

                // Show an OK button.
                View doneButton2 = findViewById(R.id.done_button);
                doneButton2.setOnClickListener(doneListener);

                doneButton2.setVisibility(View.VISIBLE);
                return true;

            case R.id.action_exit:
                // exit
                onDestroy();
                finish();
                System.exit(0);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        TextView ocrResultTextView = (TextView) findViewById(R.id.resultText);
        ocrResultTextView.setText(tem);
        TextView mTxtOutput = (TextView) findViewById(R.id.resultText);

        mTxtOutput.setMovementMethod(ScrollingMovementMethod.getInstance());

    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_show_result_actions, menu);

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public void onDestroy() {
        // Don't forget to shutdown!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
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
//          else {
//                btnSpeak.setEnabled(true);
//
//            }
        } else {
            Log.e("TTS", "Initilization Failed");
        }

        //  speakOut(tem);

    }

    public void speakOut(String text) {

        //String text = txtText.getText().toString();
        if (text.length() == 0) {
            tts.speak("You haven't typed text", TextToSpeech.QUEUE_FLUSH, null);

        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            //tts.speak("You haven't typed text", TextToSpeech.QUEUE_ADD, null);
        }

    }

    public void speakOut(TextView textView) {

        //String text = txtText.getText().toString();
        if (textView.length() == 0) {
            tts.speak("You haven't typed text", TextToSpeech.QUEUE_FLUSH, null);

        } else {
            tts.speak(textView.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
            //tts.speak("You haven't typed text", TextToSpeech.QUEUE_ADD, null);
        }

    }

}
