package com.rozmus.terry.subtitler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private boolean cameraActive = false;
    private CameraPreview mPreview;
    private FrameLayout preview;
    private ListView subtitleListView;
    private Thread subtitleThread;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private ScaleGestureDetector mScaleGestureDetector;
    private boolean playing = false;
    private boolean scrolling = false;
    private boolean touched = false;
    private long elapsed = 0;
    private long playbackStartTime = 0;
    private int currentFrame = 0;
    private int textSize = 36;

    static private List<Integer> start = new ArrayList<Integer>();
    static private List<Integer> finish = new ArrayList<Integer>();
    static private List<String> subtitle = new ArrayList<String>();

    FloatingActionButton chooseButton;
    FloatingActionButton playButton;
    FloatingActionButton pauseButton;
    FloatingActionButton cameraButton;

    Animation animHideButtons;
    Animation animShowButtons;
    Animation animShowButtonsInstant;

    private void initialiseSubtitleThread() {
        Handler handler = new Handler();
        subtitleThread = new Thread(new Runnable() {

            @Override
            public void run() {
                // Note: ListView is indexed from 0, but frames in the SRT are indexed from 1
                // This frame variable will use the ListView indexing;
                while (true) {
                    if (playing) {
                        if (touched) {

                            if (scrolling) {
                                // Handle the scrolling if the user also touched the screen
                                // (Needed to distinguish user scroll from subtitle playback
                                // which Android also detects as a scroll event)
                                // Detect when the currentFrame has settled
                                int pastFrame;
                                do {
                                    pastFrame = currentFrame;
                                    try {
                                        Thread.sleep(50);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    currentFrame = subtitleListView.getLastVisiblePosition();
                                } while (currentFrame != pastFrame);

                                // Handle scroll past beginning
                                if (currentFrame < 0) currentFrame = 0;

                                elapsed = start.get(currentFrame);
                                playbackStartTime = System.currentTimeMillis() - elapsed;

                                // Reset for next scroll detection
                                scrolling = false;
                            }
                            // Reset for next touch detection
                            touched = false;
                        } else {
                            // if user hasn't scrolled, do normal playback
                            elapsed = System.currentTimeMillis() - playbackStartTime;

                            if (currentFrame < subtitleListView.getCount()) {
                                if (start.get(currentFrame) <= elapsed) {
                                    subtitleListView.smoothScrollToPosition(currentFrame);
                                    currentFrame += 1;
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "UpdateThread");

        subtitleThread.start();
    }

    private void startSubtitleThread() {
        currentFrame = subtitleListView.getLastVisiblePosition();
        elapsed = start.get(currentFrame);
        playbackStartTime = System.currentTimeMillis() - elapsed;
        playing = true;
    }

    private void stopSubtitleThread() {
        playing = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepScreenOn();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // find camera view
        preview = (FrameLayout) findViewById(R.id.camera_view);

        // Load animations
        animHideButtons = AnimationUtils.loadAnimation(this, R.anim.anim_hide_buttons);
        animShowButtons = AnimationUtils.loadAnimation(this, R.anim.anim_show_buttons);
        animShowButtonsInstant = AnimationUtils.loadAnimation(this, R.anim.anim_show_buttons_instant);

        // Initialise the subtitle thread
        initialiseSubtitleThread();

        // Initialise the control buttons
        chooseButton = (FloatingActionButton) findViewById(R.id.choose_button);
        playButton = (FloatingActionButton) findViewById(R.id.play_button);
        pauseButton = (FloatingActionButton) findViewById(R.id.pause_button);
        cameraButton = (FloatingActionButton) findViewById(R.id.camera_button);

        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseSubtitles();
            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playSubtitles();
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pausePlayback();
            }
        });
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCamera();
            }
        });

        // Initialise the subtitle ListView
        subtitleListView = (ListView) findViewById(R.id.subtitle_list);

        // Load instructions to show on startup
        if (subtitleListView.getCount() == 0) {
            /* Couldn't get this to work
            String store = Environment.getExternalStorageDirectory().getAbsolutePath();
            String app = this.getApplicationInfo().dataDir;
            String instructionFile = store + app + "/data/instructions";
            Log.i("FileLocation", instructionFile);
            try {
                getSubtitles(instructionFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            */
            // Hard code the instructions for now
            String[] instructions = {
                    "", "",
                    "The left button is for choosing subtitles.",
                    "The right button is for turning on the camera.",
                    "The middle button is both play and pause.",
                    "Scroll up or down to fast-forward or rewind.",
                    "Pinch-zooming scales the subtitle text",
                    "Upload or save an SRT subtitle file onto the phone.",
                    "It should almost immediately be available for selection."
            };
            for (int i = 0; i < instructions.length; i++) {
                start.add(i * 2000);
                finish.add(i * 2000 + 2000);
                subtitle.add(instructions[i]);
            }
            populateSubtitleListView();
        }

        // Use scroll listener to activate fast-forwarding and rewinding detection
        // but handle it in subtitle play thread
        subtitleListView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount){
                scrolling = true;
            }

        });

        // Set a touch event on the ListView to distinguish user scroll from playback
        subtitleListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleGestureDetector.onTouchEvent(event);
                touched = true;
                buttonsShow();
                buttonsHide();
                return false;
            }
        });

        // for pinch to zoom
        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            // not used
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
            // not used
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }
            // Change the font scale
            float oldScale = 0;
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float newScale = detector.getScaleFactor();
                if (newScale > 1.0 && textSize < 80) {
                    textSize += 1;
                } else if (newScale < 1.0 && textSize > 10) {
                    textSize -= 1;
                }
                populateSubtitleListView();
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        allowScreenOff();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            String subtitleUri = data.getStringExtra("subtitle_uri");
            try {
                getSubtitles(subtitleUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            populateSubtitleListView();
        }
    }

    // Convert time in hours, minutes, seconds and millisecond parts to milliseconds only
    static private int convertTimeToMilliseconds(int h, int m, int s, int ms) {
        return  3_600_000 * h + 60_000 * m + 1_000 * s + ms;
    }

    private void chooseSubtitles() {
        pausePlayback();
        Intent myIntent = new Intent(MainActivity.this, ChooseSubtitles.class);
        MainActivity.this.startActivityForResult(myIntent, 0);
    }

    @SuppressLint("RestrictedApi")
    private void playSubtitles() {
        // Run the subtitles
        startSubtitleThread();
        keepScreenOn();
        playButton.setVisibility(View.INVISIBLE);
        pauseButton.setVisibility(View.VISIBLE);
        buttonsHide();
    }

    @SuppressLint("RestrictedApi")
    private void pausePlayback() {
        allowScreenOff();
        buttonsShow();
        stopSubtitleThread();
        playButton.setVisibility(View.VISIBLE);
        pauseButton.setVisibility(View.INVISIBLE);
    }

    private void toggleCamera() {
        // Open the camera
        if (deviceHasCamera(this)) {
            if (cameraActive) {
                cameraActive = false;
                stopCamera();
            } else {
                cameraActive = true;
                startCamera();
            }
        }
    }

    private void buttonsHide() {
        // Run hide buttons if it has not already been activated and if playing
        if (chooseButton.getAlpha() == 1.0 && playing) {
            chooseButton.startAnimation(animHideButtons);
            pauseButton.startAnimation(animHideButtons);
            cameraButton.startAnimation(animHideButtons);
        }
    }

    private void buttonsShow() {
        // Run show buttons if it has not already been activated and if playing
        if (chooseButton.getAlpha() < 1.0 && playing) {
            chooseButton.startAnimation(animShowButtons);
            pauseButton.startAnimation(animShowButtons);
            cameraButton.startAnimation(animShowButtons);
        } else {
            chooseButton.startAnimation(animShowButtonsInstant);
            pauseButton.startAnimation(animShowButtonsInstant);
            cameraButton.startAnimation(animShowButtonsInstant);
        }
    }

    private void startCamera() {
        // Start Camera
        mCamera = getCameraInstance();

        // Create camera preview and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        preview.addView(mPreview);
    }

    private void stopCamera() {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        preview.removeAllViews();
    }

    private boolean deviceHasCamera(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private static void getSubtitles(String filePath) throws IOException {
        File file = new File(filePath);
        InputStream inputStream = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder subtitleFile = new StringBuilder();

        String line = "";
        do {
            subtitleFile.append(line + "\n");
            line = reader.readLine();
        } while (line != null);

        reader.close();
        inputStream.close();

        // Compile regular expression to parse the SRT
        // This regex parse pattern was acquired from Stackoverflow here:
        // https://stackoverflow.com/questions/5062914/java-api-for-srt-subtitles
        // I only added (\{.+\})? to get rid of screen position tags, which I'm not using
        String nl = "\\n";
        String sp = "[ \\t]*";

        Pattern p = Pattern.compile(
                "(\\d+)" + sp + nl
                        + "(\\d{1,2}):(\\d\\d):(\\d\\d),(\\d\\d\\d)" + sp
                        + "-->" + sp + "(\\d\\d):(\\d\\d):(\\d\\d),(\\d\\d\\d)" + sp
                        + "(X1:\\d.*?)??" + nl + "(\\{.+\\})?([^\\|]*?)" + nl + nl);

        Matcher match = p.matcher(subtitleFile.toString());

        // Empty the current subtitle list
        start.clear();
        finish.clear();
        subtitle.clear();

        // Add a couple of blank line (to prevent the first line being under the buttons
        for (int i = 0; i < 3; i++) {
            start.add(0);
            finish.add(0);
            subtitle.add("");
        }

        while (match.find()) {
            // Set frame start times
            int h = Integer.parseInt(match.group(2));
            int m = Integer.parseInt(match.group(3));
            int s = Integer.parseInt(match.group(4));
            int ms = Integer.parseInt(match.group(5));
            start.add(convertTimeToMilliseconds(h, m, s, ms));

            // Set frame finish times
            h = Integer.parseInt(match.group(6));
            m = Integer.parseInt(match.group(7));
            s = Integer.parseInt(match.group(8));
            ms = Integer.parseInt(match.group(9));
            finish.add(convertTimeToMilliseconds(h, m, s, ms));

            // Set subtitle
            subtitle.add(match.group(12));
        }
    }

    private void populateSubtitleListView() {
        // Populate listview with subtitles
        ArrayAdapter<String> subtitleListAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, subtitle) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Set the ListView item properties
                View view = super.getView(position, convertView, parent);
                TextView v = (TextView) view.findViewById(android.R.id.text1);
                v.setText(Html.fromHtml(subtitle.get(position)));
                v.setPadding(textSize, textSize, textSize, textSize);
                v.setGravity(Gravity.CENTER_HORIZONTAL);
                v.setTextColor(Color.WHITE);
                v.setTextSize(textSize);
                return view;
            }
        };
        subtitleListView.setAdapter(subtitleListAdapter);
    }

    private void keepScreenOn() {
        // Keep the screen on during playback
        if (playing) {
            super.onResume();
            powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "subtitler:wake");
            wakeLock.acquire();
        }
    }

    private void allowScreenOff() {
        // Release the screen wakelock if playing
        // (it is only acquired while playing)
        if (playing) {
            wakeLock.release();
        }
    }
}

