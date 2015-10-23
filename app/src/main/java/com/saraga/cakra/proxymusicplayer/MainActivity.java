package com.saraga.cakra.proxymusicplayer;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import com.saraga.cakra.proxymusicplayer.utils.ProxyThread;

import java.io.IOException;

public class MainActivity extends AppCompatActivity
        implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener {

    private Button mBtnPlaynPause;
    private SeekBar mProgressBar;
    private boolean mIsPrepared, mIsAutoProgress;
    private MediaPlayer mMainPlayer;
    private Handler mHandler;

    private ProxyThread mProxy;

    private Runnable mTimelyRunnable = new Runnable() {

        @Override
        public void run() {
            if (getProgress() > 100) {
                mMainPlayer.seekTo(0);
                mBtnPlaynPause.callOnClick();
            } else {
                mIsAutoProgress = true;
                mProgressBar.setProgress(getProgress());
            }
            if (mMainPlayer != null && mMainPlayer.isPlaying()) {
                mHandler.postDelayed(mTimelyRunnable, 300);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBtnPlaynPause = (Button) findViewById(R.id.btn_play);
        mProgressBar = (SeekBar) findViewById(R.id.progress);

        mHandler = new Handler();

        startProxy();

        initMusicPlayer();

        mProgressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mMainPlayer != null && mMainPlayer.isPlaying() && fromUser) {
                    mMainPlayer.seekTo(Math.round((mMainPlayer.getDuration() * progress) / 100));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mBtnPlaynPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlaying()) {
                    mBtnPlaynPause.setText("Pause");
                    play();
                } else {
                    mBtnPlaynPause.setText("Play");
                    pause();
                }
            }
        });
    }

    private void play() {
        if (mIsPrepared) {
            mMainPlayer.start();
            mHandler.post(mTimelyRunnable);
        } else {
            playFromBeginning();
        }
    }

    private int getProgress() {
        if (mMainPlayer != null) {
            return Math.round((mMainPlayer.getCurrentPosition() * 100) / mMainPlayer.getDuration());
        } else {
            return 0;
        }
    }


    private boolean isPlaying() {
        try {
            return mMainPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public void initMusicPlayer() {
        mMainPlayer = new MediaPlayer();
        mMainPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mMainPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mMainPlayer.setOnPreparedListener(this);
        mMainPlayer.setOnCompletionListener(this);
        mMainPlayer.setOnErrorListener(this);
    }

    public void playFromBeginning() {
        mMainPlayer.reset();
        try {
            mMainPlayer.setDataSource("http://127.0.0.1:8080?url=http://db.oruwebsite.com/Tamil/Songs/25%20-%20Religous%20Albums/Devotionals/Brinthavanam%20Nithiyasree%20Carnatic/Kanna%20Vaa.mp3");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMainPlayer.prepareAsync();
    }

    private void pause() {
        mMainPlayer.pause();
    }

    private void startProxy() {
        mProxy = new ProxyThread();
        new Thread(mProxy).start();
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {

    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        if(mediaPlayer.equals(mMainPlayer)) {
            mIsPrepared = true;
            mMainPlayer.seekTo(Math.round(0));
            play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMainPlayer.release();
        mMainPlayer = null;
        if (mProxy != null) {
            mProxy.release();
            mProxy = null;
        }
    }
}
