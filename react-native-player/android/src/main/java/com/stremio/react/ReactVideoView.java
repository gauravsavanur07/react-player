package com.stremio.react;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import bolts.Task;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_SPACE;

// This originally extended ScalableVideoView, which extends TextureView
// Now we extend SurfaceView (https://github.com/crosswalk-project/crosswalk-website/wiki/Android-SurfaceView-vs-TextureView)
public class ReactVideoView extends SurfaceView implements IVLCVout.Callback, MediaPlayer.EventListener, LifecycleEventListener, View.OnKeyListener {

    public enum Events {
        EVENT_LOAD_START("onVideoLoadStart"),
        EVENT_LOAD("onVideoLoad"),
        EVENT_ERROR("onVideoError"),
        EVENT_PROGRESS("onVideoProgress"),
        EVENT_PAUSE("onVideoPause"),
        EVENT_PLAY("onVideoPlay"),
        EVENT_SEEK("onVideoSeek"),
        EVENT_END("onVideoEnd"),
        EVENT_STALLED("onPlaybackStalled"),
        EVENT_RESUME("onPlaybackResume"),
        EVENT_NEW_LAYOUT("onNewLayout");
        private final String mName;

        Events(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    private static final String TAG = "RCTVLC";
    private static final double MIN_PROGRESS_INTERVAL = 0.1;

    private static final int D_PAD_SEEK_TIME = 30000;
    private static final int D_PAD_SEEK_REPEAT_COUNT = 14;
    private static final int D_PAD_SEEK_MAX_MULTIPLIER = 5;

    public static final String EVENT_PROP_DURATION = "duration";
    //public static final String EVENT_PROP_PLAYABLE_DURATION = "playableDuration";
    public static final String EVENT_PROP_CURRENT_TIME = "currentTime";
    public static final String EVENT_PROP_SEEK_TIME = "seekTime";
    public static final String EVENT_PROP_WIDTH = "width";
    public static final String EVENT_PROP_HEIGHT = "height";

    public static final String EVENT_PROP_ERROR = "error";
    public static final String EVENT_PROP_WHAT = "what";
    public static final String EVENT_PROP_EXTRA = "extra";

    public static final String EVENT_PROP_BUFFERING_PROG = "progress";

    private ThemedReactContext mThemedReactContext;
    private RCTEventEmitter mEventEmitter;

    private String mSrcUriString = null;
    private boolean mPaused = false;
    private float mVolume = 1.0f;
    private boolean mLoaded = false;
    private boolean mStalled = false;
    private double mPrevProgress = 0.0;

    private LibVLC libvlc;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private int rootViewWidth;
    private int rootViewHeight;
    private int orientation;

    public ReactVideoView(ThemedReactContext themedReactContext) {
        super(themedReactContext);

        mThemedReactContext = themedReactContext;
        mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        themedReactContext.addLifecycleEventListener(this);
        orientation = mThemedReactContext.getResources().getConfiguration().orientation;

        createPlayer();
    }

    private void createPlayer() {
        if (mMediaPlayer != null) return;

        try {
            // Create LibVLC
            ArrayList<String> options = new ArrayList<String>();
            //options.add("--subsdec-encoding <encoding>");
            //options.add("--aout=opensles");
            //options.add("--audio-time-stretch"); // time stretching
            options.add("-vvv"); // verbosity
            options.add("--http-reconnect");
            //options.add("--network-caching="+(8*1000));
            libvlc = new LibVLC(mThemedReactContext, options);
            this.getHolder().setKeepScreenOn(true);

            // Create media player
            mMediaPlayer = new MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(this);

            // Set up video output
            final IVLCVout vout = mMediaPlayer.getVLCVout();
            vout.setVideoView(this);
            vout.addCallback(this);
            vout.attachViews();
        } catch (Exception e) {
            // TODO onError
        }
    }

    private void releasePlayer() {
        if (libvlc == null) return;
        Task.callInBackground(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                mMediaPlayer.stop();
                final IVLCVout vout = mMediaPlayer.getVLCVout();
                vout.removeCallback(ReactVideoView.this);
                vout.detachViews();
                libvlc.release();
                libvlc = null;
                return null;
            }

        });
    }

    private void setLayout() {
        if (mVideoHeight * mVideoWidth == 0) return;

        double aspectRatio = (double) mVideoWidth / (double) mVideoHeight;
        double displayAspectRatio = (double) rootViewWidth / (double) rootViewHeight;

        int newWidth, newHeight;
        if (aspectRatio > displayAspectRatio) {
            newWidth = rootViewWidth;
            newHeight = (int) (rootViewWidth / aspectRatio);
        } else {
            newWidth = (int) (rootViewHeight * aspectRatio);
            newHeight = rootViewHeight;
        }
        int xoff = (rootViewWidth - newWidth) / 2;
        int yoff = (rootViewHeight - newHeight) / 2;

        WritableMap event = Arguments.createMap();
        event.putInt("xoff", xoff);
        event.putInt("yoff", yoff);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_NEW_LAYOUT.toString(), event);
    }

    @Override
    public void onNewLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width * height == 0) return;

        mVideoWidth = width;
        mVideoHeight = height;

        rootViewWidth = getRootView().getWidth();
        rootViewHeight = getRootView().getHeight();

        setLayout();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (orientation != newConfig.orientation) {
            int swap = rootViewHeight;
            rootViewHeight = rootViewWidth;
            rootViewWidth = swap;
            orientation = newConfig.orientation;
            setLayout();
        }
    }

    @Override
    public void onSurfacesCreated(IVLCVout vout) {

    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vout) {

    }

    public void setSrc(final String uriString) {

        mSrcUriString = uriString;

        createPlayer();

        Media m = new Media(libvlc, Uri.parse(uriString));
        mMediaPlayer.setMedia(m);
        //mMediaPlayer.play(); // Maybe it's better to call that only through updateModifiers()

        // We need this because on the Opening event, we do not get Duration
        mLoaded = false;

        // Because the VLC buffering event and state is a bit wonky
        mStalled = false;

        WritableMap src = Arguments.createMap();
        src.putString(ReactVideoViewManager.PROP_SRC_URI, uriString);
        WritableMap event = Arguments.createMap();
        event.putMap(ReactVideoViewManager.PROP_SRC, src);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD_START.toString(), event);
    }

    public void setPausedModifier(final boolean paused) {
        mPaused = paused;

        if (mPaused) {
            mMediaPlayer.pause();
        } else {
            mMediaPlayer.play();
        }
    }

    public void setVolumeModifier(final float volume) {
        mVolume = volume;
        mMediaPlayer.setVolume((int) volume * 200);
    }

    public void applyModifiers() {
        setPausedModifier(mPaused);
    }

    public void seekTo(int msec) {
        WritableMap event = Arguments.createMap();
        event.putDouble(EVENT_PROP_CURRENT_TIME, mMediaPlayer.getTime() / 1000.0);
        event.putDouble(EVENT_PROP_SEEK_TIME, msec / 1000.0);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_SEEK.toString(), event);

        mMediaPlayer.setTime(msec);
    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vout) {
        // Handle errors with hardware acceleration
        WritableMap error = Arguments.createMap();
        error.putString(EVENT_PROP_WHAT, "Error with hardware acceleration");
        WritableMap event = Arguments.createMap();
        event.putMap(EVENT_PROP_ERROR, error);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_ERROR.toString(), event);
    }

    @Override
    public void onEvent(MediaPlayer.Event ev) {
        WritableMap event = Arguments.createMap();

        switch(ev.type) {
            case MediaPlayer.Event.EndReached:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_END.toString(), null);
                releasePlayer();
                break;
            case MediaPlayer.Event.EncounteredError:
                WritableMap error = Arguments.createMap();
                error.putString(EVENT_PROP_WHAT, "MediaPlayer.Event.EncounteredError");
                // TODO: more info
                event.putMap(EVENT_PROP_ERROR, error);
                mEventEmitter.receiveEvent(getId(), Events.EVENT_ERROR.toString(), event);
                releasePlayer();
                break;
            case MediaPlayer.Event.Buffering:
                float buffering = ev.getBuffering();
                event.putDouble(EVENT_PROP_BUFFERING_PROG, buffering);
                if (buffering < 30 && !mStalled) {
                    mStalled = true;
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_STALLED.toString(), event);
                }
                break;
            case MediaPlayer.Event.Playing:
                if (! mLoaded) {
                    event.putDouble(EVENT_PROP_DURATION, mMediaPlayer.getLength() / 1000.0);
                    event.putDouble(EVENT_PROP_CURRENT_TIME, mMediaPlayer.getTime() / 1000.0);

                    mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD.toString(), event);

                    mLoaded = true;
                }

                mEventEmitter.receiveEvent(getId(), Events.EVENT_PLAY.toString(), null);
                this.getHolder().setKeepScreenOn(true);
                break;
            case MediaPlayer.Event.Paused:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_PAUSE.toString(), null);
                this.getHolder().setKeepScreenOn(false);
                break;
            case MediaPlayer.Event.Stopped:
                this.getHolder().setKeepScreenOn(false);
                break;
            case MediaPlayer.Event.Opening:
                // We may want to move that to first Playing?
                applyModifiers();
                break;
            case MediaPlayer.Event.TimeChanged:
                double currentProgress = mMediaPlayer.getTime() / 1000.0;
                if (Math.abs(currentProgress - mPrevProgress) >= MIN_PROGRESS_INTERVAL || currentProgress == 0) {
                    mPrevProgress = currentProgress;
                    event.putDouble(EVENT_PROP_CURRENT_TIME, currentProgress);
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_PROGRESS.toString(), event);
                }

                // 3 is Playing, can't find the enum for some reason
                if (mMediaPlayer.getPlayerState() == 3 && mStalled) {
                    mStalled = false;
                    event = Arguments.createMap();
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_RESUME.toString(), event);
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releasePlayer();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSrc(mSrcUriString);
    }

    @Override
    public void onHostPause() {
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
    }

    @Override
    public void onHostResume() {
        if (mMediaPlayer != null) {
            IVLCVout vout = mMediaPlayer.getVLCVout();
            if (vout != null) {
                if (!vout.areViewsAttached()) {
                    vout.setVideoView(this);
                    vout.attachViews();
                }
            }
        }
    }

    @Override
    public void onHostDestroy() {
    }

    @Override
    public boolean onKey(View view, int i, KeyEvent keyEvent) {
        final int action = keyEvent.getAction();
        final int keyCode = keyEvent.getKeyCode();
        final int repeatCount = keyEvent.getRepeatCount();

        if (action == ACTION_DOWN) {
            switch (keyCode) {
                case KEYCODE_SPACE:
                case KEYCODE_DPAD_CENTER:
                case KEYCODE_MEDIA_PLAY_PAUSE:
                    if (repeatCount == 0) {
                        if (mMediaPlayer.isPlaying()) {
                            mMediaPlayer.pause();
                        } else {
                            mMediaPlayer.play();
                        }
                    }

                    break;
                case KEYCODE_DPAD_LEFT:
                case KEYCODE_DPAD_RIGHT:
                    if (mMediaPlayer.isSeekable()) {
                        int multiplier = 1;

                        if (repeatCount > 0) {
                            if (repeatCount % D_PAD_SEEK_REPEAT_COUNT != 0) {
                                break;
                            }

                            multiplier = Math.min(1 + repeatCount / D_PAD_SEEK_REPEAT_COUNT, D_PAD_SEEK_MAX_MULTIPLIER);
                        }

                        if (keyCode == KEYCODE_DPAD_LEFT) {
                            multiplier *= -1;
                        }

                        int seekTime = (int) mMediaPlayer.getTime() + (multiplier * D_PAD_SEEK_TIME);
                        if (seekTime < 0) seekTime = 0;
                        seekTo(seekTime);
                    }

                    break;
            }
        }

        return false;
    }

}
