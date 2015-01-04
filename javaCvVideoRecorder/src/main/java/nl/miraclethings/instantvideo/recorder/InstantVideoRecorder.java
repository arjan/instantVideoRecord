package nl.miraclethings.instantvideo.recorder;

import android.content.Context;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.cpp.opencv_core;

import java.io.File;
import java.nio.Buffer;
import java.nio.ShortBuffer;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;

/**
 * Main recorder class
 * Created by Arjan Scherpenisse on 4-1-15.
 */
public class InstantVideoRecorder implements Camera.PreviewCallback {

    private final Context mContext;

    private String strAudioPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_audio.mp4";
    private String strVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_video.mp4";
    private String strFinalPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_final.mp4";

    private int currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;

    private File fileAudioPath = null;
    private File fileVideoPath = null;
    private Uri uriVideoPath = null;

    private File tempFolderPath = null;
    private int sampleRate = 44100;
    private int frameRate = 30;
    private long frameTime = 0L;

    /* audio data getting thread */
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    volatile boolean runAudioThread = true;

    private volatile FFmpegFrameRecorder audioRecorder;
    private volatile FFmpegFrameRecorder videoRecorder;
    private long firstTime;
    private boolean recording;
    private volatile long mAudioTimestamp = 0L;

    private final int[] mVideoRecordLock = new int[0];
    private final int[] mAudioRecordLock = new int[0];
    private long mLastAudioTimestamp = 0L;
    private volatile long mAudioTimeRecorded;

    private SavedFrames lastSavedframe = new SavedFrames(null, 0L);
    private long mVideoTimestamp = 0L;
    private opencv_core.IplImage yuvIplImage;

    public InstantVideoRecorder(Context context) {

        mContext = context;
        tempFolderPath = Util.getTempFolderPath();
        if (tempFolderPath != null)
            tempFolderPath.mkdirs();


        initAudioRecorder();
        initVideoRecorder();
    }

    private void initAudioRecorder() {

        // Create a new unique path for video to be created
        strAudioPath = Util.createTempPath(tempFolderPath);
        RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);

        sampleRate = recorderParameters.getAudioSamplingRate();
        frameRate = recorderParameters.getVideoFrameRate();
        frameTime = (1000000L / frameRate);
        fileAudioPath = new File(strAudioPath);

        audioRecorder = new FFmpegFrameRecorder(strAudioPath, 320, 240, 1);
        audioRecorder.setFormat(recorderParameters.getVideoOutputFormat());
        audioRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
        audioRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
        audioRecorder.setVideoCodec(recorderParameters.getVideoCodec());
        audioRecorder.setVideoQuality(recorderParameters.getVideoQuality());
        audioRecorder.setAudioQuality(recorderParameters.getVideoQuality());

        audioRecorder.setAudioCodec(recorderParameters.getAudioCodec());


        audioRecorder.setVideoBitrate(recorderParameters.getVideoBitrate());
        audioRecorder.setAudioBitrate(recorderParameters.getAudioBitrate());

        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
    }


    private void initVideoRecorder() {
        strVideoPath = Util.createTempPath(tempFolderPath);
        RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);
        fileVideoPath = new File(strVideoPath);
        videoRecorder = new FFmpegFrameRecorder(strVideoPath, 320, 240, 1);
        videoRecorder.setFormat(recorderParameters.getVideoOutputFormat());
        videoRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
        videoRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
        videoRecorder.setVideoCodec(recorderParameters.getVideoCodec());
        videoRecorder.setVideoQuality(recorderParameters.getVideoQuality());
        videoRecorder.setAudioQuality(recorderParameters.getVideoQuality());
        videoRecorder.setAudioCodec(recorderParameters.getAudioCodec());
        videoRecorder.setVideoBitrate(1000000);
        videoRecorder.setAudioBitrate(64000);
    }

    public Uri getFilePath() {
        return Uri.fromFile(new File(strFinalPath));
    }

    public void setSize(int previewWidth, int previewHeight) {
        videoRecorder.setImageWidth(previewWidth);
        videoRecorder.setImageHeight(previewHeight);

        yuvIplImage = opencv_core.IplImage.create(previewWidth, previewHeight, IPL_DEPTH_8U, 2);

    }

    public void startRecording() {
        firstTime = System.currentTimeMillis();
        recording = true;
        try {
            videoRecorder.start();
            audioRecorder.start();
            audioThread.start();

        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public boolean stopRecording(final Runnable onFinish) {

        if (!recording) {
            return false;
        }

        runAudioThread = false;
        // This will make the executor accept no new threads
        // and finish all existing threads in the queue

        //  pool.shutdown();
        // Wait until all threads are finish
        // pool.awaitTermination(firstTime, null);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                runAudioThread = false;
                super.onPreExecute();
            }

            @Override
            protected Void doInBackground(Void... params) {
                strFinalPath = Util.createFinalPath();
                System.out.println(strVideoPath);
                System.out.println(strAudioPath);
                System.out.println(strFinalPath);
                Util.combineVideoAndAudio(mContext, strVideoPath, strAudioPath, strFinalPath);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                releaseResources();
                onFinish.run();
            }
        }.execute();

        return true;
    }

    public void setCamera(Camera mCamera) {
        mCamera.setPreviewCallback(this);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
            /* get video data */
        long frameTimeStamp = 0L;
        if (mAudioTimestamp == 0L && firstTime > 0L)
            frameTimeStamp = 1000L * (System.currentTimeMillis() - firstTime);
        else if (mLastAudioTimestamp == mAudioTimestamp)
            frameTimeStamp = mAudioTimestamp + frameTime;
        else {
            long l2 = (System.nanoTime() - mAudioTimeRecorded) / 1000L;
            frameTimeStamp = l2 + mAudioTimestamp;
            mLastAudioTimestamp = mAudioTimestamp;
        }
        synchronized (mVideoRecordLock) {
            if (recording && lastSavedframe != null && lastSavedframe.getFrameBytesData() != null && yuvIplImage != null) {
                mVideoTimestamp += frameTime;
                if (lastSavedframe.getTimeStamp() > mVideoTimestamp)
                    mVideoTimestamp = lastSavedframe.getTimeStamp();
                try {
                    yuvIplImage.getByteBuffer().put(lastSavedframe.getFrameBytesData());
                    videoRecorder.setTimestamp(lastSavedframe.getTimeStamp());
                    videoRecorder.record(yuvIplImage);
                } catch (com.googlecode.javacv.FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
            lastSavedframe = new SavedFrames(data, frameTimeStamp);
        }
    }

    //---------------------------------------------
// audio thread, gets and encodes audio data
//---------------------------------------------
    class AudioRecordRunnable implements Runnable {


        // Audio
        int bufferSize;
        short[] audioData;
        int bufferReadResult;
        private final AudioRecord audioRecord;
        public volatile boolean isInitialized;
        private int mCount = 0;

        private AudioRecordRunnable() {
            bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioData = new short[bufferSize];
        }

        private void record(ShortBuffer shortBuffer) {
            try {
                synchronized (mAudioRecordLock) {
                    if (audioRecorder != null) {
                        this.mCount += shortBuffer.limit();
                        audioRecorder.record(new Buffer[]{shortBuffer});
                    }
                    return;
                }
            } catch (FrameRecorder.Exception localException) {
            }
        }

        private void updateTimestamp() {
            if (audioRecorder != null) {
                int i = Util.getTimeStampInNsFromSampleCounted(this.mCount);
                if (mAudioTimestamp != i) {
                    mAudioTimestamp = i;
                    mAudioTimeRecorded = System.nanoTime();
                }
            }
        }

        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            this.isInitialized = false;
            if (audioRecord != null) {
                while (this.audioRecord.getState() == 0) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException localInterruptedException) {
                    }
                }
                this.isInitialized = true;
                this.audioRecord.startRecording();
                while (((runAudioThread) || (mVideoTimestamp > mAudioTimestamp)) && (mAudioTimestamp < (1000 * 60 * 1000))) {
                    updateTimestamp();
                    bufferReadResult = this.audioRecord.read(audioData, 0, audioData.length);
                    if ((bufferReadResult > 0) && ((recording) || (mVideoTimestamp > mAudioTimestamp)))
                        record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
                }
                this.audioRecord.stop();
                this.audioRecord.release();
            }
        }

    }

    public void releaseResources() {

        if (fileAudioPath != null && fileAudioPath.exists())
            fileAudioPath.delete();
        if (fileVideoPath != null && fileVideoPath.exists())
            fileVideoPath.delete();

        recording = false;
        try {
            if (videoRecorder != null) {
                videoRecorder.stop();
                videoRecorder.release();
            }
            if (audioRecorder != null) {
                audioRecorder.stop();
                audioRecorder.release();
            }
        } catch (com.googlecode.javacv.FrameRecorder.Exception e) {
            e.printStackTrace();
        }

        yuvIplImage = null;
        videoRecorder = null;
        audioRecorder = null;
        lastSavedframe = null;
    }


}
