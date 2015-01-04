instantVideoRecord
==================

No-latency video recording library for Android using ffmpeg.

The standard [MediaRecorder](http://developer.android.com/reference/android/media/MediaRecorder.html MediaRecorder) class has an annoying startup time before the actual recording starts. What makes it worse is that this internal initialization latency (which occurs between issuing the `.start()` command and the actual recording) is device-dependent.

This library uses FFMPEG and the camera preview frame to manually encode video frames and then uses ffmpeg to combine it with recorded audio.
