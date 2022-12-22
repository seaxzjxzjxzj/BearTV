package com.fongmi.android.tv.player;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Prefers;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.Util;

import java.util.Formatter;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class Players implements Player.Listener, IMediaPlayer.OnInfoListener, IMediaPlayer.OnErrorListener, IMediaPlayer.OnPreparedListener, IMediaPlayer.OnCompletionListener, AnalyticsListener, ParseTask.Callback {

    private IjkVideoView ijkPlayer;
    private StringBuilder builder;
    private Formatter formatter;
    private ParseTask parseTask;
    private ExoPlayer exoPlayer;
    private int errorCode;
    private int retry;
    private int decode;
    private int player;

    public boolean isExo() {
        return player == 0;
    }

    public boolean isIjk() {
        return player == 1;
    }

    public Players init() {
        player = Prefers.getPlayer();
        decode = Prefers.getDecode();
        builder = new StringBuilder();
        formatter = new Formatter(builder, Locale.getDefault());
        return this;
    }

    public void setupIjk(IjkVideoView view) {
        ijkPlayer = view;
        ijkPlayer.setDecode(decode);
        ijkPlayer.setOnInfoListener(this);
        ijkPlayer.setOnErrorListener(this);
        ijkPlayer.setOnPreparedListener(this);
        ijkPlayer.setOnCompletionListener(this);
    }

    public void setupExo(StyledPlayerView view) {
        if (exoPlayer != null) releaseExo();
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(new DefaultLoadControl()).setRenderersFactory(ExoUtil.buildRenderersFactory()).setTrackSelector(ExoUtil.buildTrackSelector()).build();
        exoPlayer.addAnalyticsListener(this);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
    }

    public ExoPlayer exo() {
        return exoPlayer;
    }

    public int getPlayer() {
        return player;
    }

    public int getDecode() {
        return decode;
    }

    public void reset() {
        this.errorCode = 0;
        this.retry = 0;
    }

    public int getRetry() {
        return retry;
    }

    public int addRetry() {
        ++retry;
        return retry;
    }

    public String stringToTime(long time) {
        return Util.getStringForTime(builder, formatter, time);
    }

    public float getSpeed() {
        return isExo() ? exoPlayer.getPlaybackParameters().speed : ijkPlayer.getSpeed();
    }

    public long getPosition() {
        return isExo() ? exoPlayer.getCurrentPosition() : ijkPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return isExo() ? exoPlayer.getDuration() : ijkPlayer.getDuration();
    }

    public long getBuffered() {
        return isExo() ? exoPlayer.getBufferedPosition() : ijkPlayer.getBufferedPosition();
    }

    public boolean isPlaying() {
        return isExo() ? exoPlayer.isPlaying() : ijkPlayer.isPlaying();
    }

    private int getVideoWidth() {
        return isExo() ? exoPlayer.getVideoSize().width : ijkPlayer.getVideoWidth();
    }

    private int getVideoHeight() {
        return isExo() ? exoPlayer.getVideoSize().height : ijkPlayer.getVideoHeight();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player)[player];
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        exoPlayer.setPlaybackSpeed(speed);
        ijkPlayer.setSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed == 5 ? 0.25f : speed + addon;
        exoPlayer.setPlaybackSpeed(speed);
        ijkPlayer.setSpeed(speed);
        return getSpeedText();
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        exoPlayer.setPlaybackSpeed(speed);
        ijkPlayer.setSpeed(speed);
        return getSpeedText();
    }

    public void setPlayer(int player) {
        this.player = player;
    }

    public void togglePlayer() {
        setPlayer(player == 0 ? 1 : 0);
        Prefers.putPlayer(player);
    }

    public void setDecode(int decode) {
        this.decode = decode;
    }

    public void toggleDecode() {
        setDecode(decode == 0 ? 1 : 0);
        Prefers.putDecode(decode);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        if (isExo()) exoPlayer.seekTo(getPosition() + time);
        else if (isIjk()) ijkPlayer.seekTo(getPosition() + time);
    }

    public void seekTo(long time, boolean force) {
        if (time == 0 && !force) return;
        if (isExo()) exoPlayer.seekTo(time);
        else if (isIjk()) ijkPlayer.seekTo(time);
    }

    public void play() {
        if (isExo()) exoPlayer.play();
        else if (isIjk()) ijkPlayer.start();
    }

    public void pause() {
        if (isExo()) pauseExo();
        else if (isIjk()) pauseIjk();
    }

    public void stop() {
        reset();
        if (isExo()) stopExo();
        else if (isIjk()) stopIjk();
    }

    public void release() {
        stopParse();
        if (isExo()) releaseExo();
        else if (isIjk()) releaseIjk();
    }

    public boolean isRelease() {
        return exoPlayer == null || ijkPlayer == null;
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000;
    }

    public void start(Channel channel) {
        setMediaSource(channel.getHeaders(), channel.getUrl());
    }

    public void start(Result result, boolean useParse) {
        if (result.getUrl().isEmpty()) {
            PlayerEvent.error(R.string.error_play_load);
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            stopParse();
            parseTask = ParseTask.create(this).run(result, useParse);
        } else {
            setMediaSource(result);
        }
    }

    private void pauseExo() {
        exoPlayer.pause();
    }

    private void pauseIjk() {
        ijkPlayer.pause();
    }

    private void stopExo() {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
    }

    private void stopIjk() {
        ijkPlayer.stopPlayback();
    }

    private void releaseExo() {
        stopExo();
        exoPlayer.removeListener(this);
        exoPlayer.release();
        exoPlayer = null;
    }

    private void releaseIjk() {
        stopIjk();
        ijkPlayer.release(true);
        ijkPlayer = null;
    }

    private void stopParse() {
        if (parseTask != null) parseTask.cancel();
    }

    private void setMediaSource(Result result) {
        SpiderDebug.log(errorCode + "," + result.getUrl() + "," + result.getHeaders());
        if (isIjk()) ijkPlayer.setMediaSource(result.getPlayUrl() + result.getUrl(), result.getHeaders());
        if (isExo()) exoPlayer.setMediaSource(ExoUtil.getSource(result, errorCode));
        if (isExo()) exoPlayer.prepare();
        PlayerEvent.state(0);
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        SpiderDebug.log(errorCode + "," + url + "," + headers);
        if (isIjk()) ijkPlayer.setMediaSource(url, headers);
        if (isExo()) exoPlayer.setMediaSource(ExoUtil.getSource(headers, url, errorCode));
        if (isExo()) exoPlayer.prepare();
        PlayerEvent.state(0);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (from.length() > 0) Notify.show(ResUtil.getString(R.string.parse_from, from));
        setMediaSource(headers, url);
    }

    @Override
    public void onParseError() {
        PlayerEvent.error(R.string.error_play_parse);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        this.errorCode = error.errorCode;
        PlayerEvent.error(R.string.error_play_format, true);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        PlayerEvent.state(state);
    }

    @Override
    public void onAudioSinkError(@NonNull EventTime eventTime, @NonNull Exception audioSinkError) {
        seekTo(200);
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                PlayerEvent.state(Player.STATE_BUFFERING);
                return true;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                PlayerEvent.state(Player.STATE_READY);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        PlayerEvent.error(R.string.error_play_format, true);
        return true;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        PlayerEvent.state(Player.STATE_READY);
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        PlayerEvent.state(Player.STATE_ENDED);
    }
}
