package com.sourab.touch.to.record.demo;


import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;
import android.widget.VideoView;

public class MainActivity extends Activity implements OnClickListener {
    Button btnStart;
    public static final int CUSTOM_ACTION_VIDEO_CAPTURE = 101;
    VideoView videoView = null;
    public static final int MAX_VIDEO_DURATION_ALLOWED = 5 * 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
    }

    private void initialize() {
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStart.setOnClickListener(this);
        videoView = (VideoView) findViewById(R.id.videoView);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnStart:
                if (videoView != null && videoView.isPlaying()) {
                    finish();
                } else {
                    Intent intent = new Intent(MainActivity.this, FFmpegRecorderActivity.class);
                    startActivityForResult(intent, CUSTOM_ACTION_VIDEO_CAPTURE);
                }
                break;
            default:
                break;
        }

    }

    private void playRecordedVideo(Uri videoUri, boolean playVideoInLoop) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        videoView.setLayoutParams(layoutParams);

        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(videoUri);
        if (playVideoInLoop) {
            MediaController mediaController = new MediaController(MainActivity.this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setOnPreparedListener(new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                }
            });
        } else {
            videoView.setMediaController(null);
        }
        videoView.start();
        btnStart.setText(getString(R.string.txt_finish));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        getRecordedVideo(resultCode, data);
    }


    void getRecordedVideo(int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED) {
            finishAsError();
            return;
        }
        if (resultCode == RESULT_OK && null != data) {
            Uri filePath = data.getData();
            playRecordedVideo(filePath, true);
        }

    }

    private void finishAsError() {
        Toast.makeText(this, getString(R.string.unable_to_get_file), Toast.LENGTH_SHORT).show();
    }

}
