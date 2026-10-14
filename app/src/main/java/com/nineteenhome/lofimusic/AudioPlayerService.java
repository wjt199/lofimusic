package com.nineteenhome.lofimusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.*;
import androidx.media.session.MediaButtonReceiver;
import androidx.media.session.*;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("ALL")
public class AudioPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "AudioPlayerService";
    private static final String CHANNEL_ID = "LofiMusicPlaybackChannel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_TOGGLE_PLAY_PAUSE = "com.nineteenhome.lofimusic.ACTION_TOGGLE_PLAY_PAUSE";
    private static final String ACTION_PREV = "com.nineteenhome.lofimusic.ACTION_PREV";
    private static final String ACTION_NEXT = "com.nineteenhome.lofimusic.ACTION_NEXT";
    private static final String ACTION_STOP = "com.nineteenhome.lofimusic.ACTION_STOP";

    private ExoPlayer exoPlayer;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private final IBinder binder = new LocalBinder();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private boolean isPrepared = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private List<RadioStation> stationList;
    private int currentIndex = 0;

    // 音频焦点管理
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;

    public interface PlaybackStateListener {
        void onPlaybackStateChanged(boolean isPlaying);
        void onStationChanged(int index);
    }

    private PlaybackStateListener stateListener;

    public void setStateListener(PlaybackStateListener listener) {
        this.stateListener = listener;
    }

    public class LocalBinder extends Binder {
        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initializeAudioManager();
        initializePlayer();
        initializeMediaSession();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PREV: playPrevious(); break;
                case ACTION_NEXT: playNext(); break;
                case ACTION_TOGGLE_PLAY_PAUSE: togglePlayPause(); break;
                case ACTION_STOP: stopPlayback(); break;
            }
        }

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setStationList(List<RadioStation> list, int startIndex) {
        this.stationList = list;
        this.currentIndex = startIndex;
    }

    public List<RadioStation> getStationList() {
        return stationList;
    }

    private void initializeAudioManager() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(this, mainHandler)
                    .build();
        }
    }

    private void requestAudioFocus() {
        if (hasAudioFocus || audioManager == null) return;
        try {
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } catch (Exception e) {
            hasAudioFocus = true; // 即使失败也允许播放
        }
    }

    private void abandonAudioFocus() {
        if (!hasAudioFocus || audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to abandon audio focus", e);
        }
        hasAudioFocus = false;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasAudioFocus = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                hasAudioFocus = false;
                if (exoPlayer != null && exoPlayer.isPlaying()) exoPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (exoPlayer != null && exoPlayer.isPlaying()) exoPlayer.pause();
                break;
        }
    }

    private void initializePlayer() {
        com.google.android.exoplayer2.audio.AudioAttributes audioAttributes = new com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36");

        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(defaultHeaders.get("User-Agent"))
                .setDefaultRequestProperties(defaultHeaders);

        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(httpDataSourceFactory))
                .build();

        exoPlayer.setAudioAttributes(audioAttributes, false);
        exoPlayer.setPlayWhenReady(false);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && !isPrepared) {
                    isPrepared = true;
                    updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
                } else if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    updateMediaSessionState(PlaybackStateCompat.STATE_STOPPED);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                updateMediaSessionState(state);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());

                mainHandler.post(() -> {
                    if (stateListener != null) stateListener.onPlaybackStateChanged(isPlaying);
                });
            }
        });
    }

    // ================= 核心播放逻辑 (已增加 .m3u 解析支持) =================
    public void playStationAt(int index) {
        if (stationList == null || index < 0 || index >= stationList.size()) return;
        this.currentIndex = index;
        RadioStation station = stationList.get(index);

        isPrepared = false;
        if (stateListener != null) stateListener.onStationChanged(index);

        String url = station.url;
        if (url == null) return;

        requestAudioFocus();

        // 【新增】：判断是否为纯文本 .m3u 播放列表（排除 .m3u8，因为 .m3u8 是 HLS，ExoPlayer 原生支持）
        if (url.toLowerCase().endsWith(".m3u") && !url.toLowerCase().contains(".m3u8")) {
            fetchAndPlayM3uStream(url, station);
        } else if (url.contains("bilibili.com")) {
            fetchAndPlayBiliStream(url);
        } else {
            playDirectUrl(url, station);
        }
    }

    // 【新增】：处理 .m3u 播放列表文件
    private void fetchAndPlayM3uStream(String m3uUrl, RadioStation station) {
        networkExecutor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build();
                Request request = new Request.Builder()
                        .url(m3uUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String contentType = response.header("Content-Type");
                        String finalUrl = response.request().url().toString(); // 获取重定向后的真实 URL

                        // 如果服务器直接返回了音频流，直接播放
                        if (contentType != null && (contentType.startsWith("audio/") || contentType.contains("octet-stream"))) {
                            playDirectUrl(finalUrl, station);
                            return;
                        }

                        // 否则，当作纯文本播放列表解析
                        String body = response.body().string();
                        String realStreamUrl = parseM3uContent(body);

                        if (realStreamUrl != null) {
                            playDirectUrl(realStreamUrl, station);
                        } else {
                            // 降级处理：解析失败，尝试用原 URL 播放
                            playDirectUrl(m3uUrl, station);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "获取 m3u 文件失败", e);
            }
        });
    }

    // 【新增】：解析 m3u 文本，提取真实的流地址
    private String parseM3uContent(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            // 忽略空行和 # 开头的注释行
            if (!line.isEmpty() && !line.startsWith("#")) {
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    return line;
                }
            }
        }
        return null;
    }

    // 【优化】：提取公共的直接播放方法
    private void playDirectUrl(String url, RadioStation station) {
        mainHandler.post(() -> {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36");
            httpDataSourceFactory.setDefaultRequestProperties(headers);

            MediaItem mediaItem = new MediaItem.Builder().setUri(url).build();
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();

            updateMediaMetadata(station.name, station.style);
        });
    }
    // =====================================================================

    public void playPrevious() {
        if (currentIndex > 0) playStationAt(currentIndex - 1);
    }

    public void playNext() {
        if (stationList != null && currentIndex < stationList.size() - 1) playStationAt(currentIndex + 1);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    private void fetchAndPlayBiliStream(String pageUrl) {
        networkExecutor.execute(() -> {
            try {
                String roomId = pageUrl.replaceAll(".*live.bilibili.com/(\\d+).*", "$1");
                String apiUrl = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id=" + roomId + "&protocol=0,1&format=0,2&codec=0,1&qn=10000";

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(apiUrl).addHeader("User-Agent", "Mozilla/5.0").build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String streamUrl = parseBiliStreamUrl(response.body().string());
                        if (streamUrl != null) {
                            mainHandler.post(() -> {
                                Map<String, String> biliHeaders = new HashMap<>();
                                biliHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                                biliHeaders.put("Referer", "https://live.bilibili.com/" + roomId);
                                httpDataSourceFactory.setDefaultRequestProperties(biliHeaders);

                                MediaItem mediaItem = new MediaItem.Builder().setUri(streamUrl).build();
                                exoPlayer.setMediaItem(mediaItem);
                                exoPlayer.prepare();
                                exoPlayer.play();

                                if (stationList != null && currentIndex < stationList.size()) {
                                    updateMediaMetadata(stationList.get(currentIndex).name, stationList.get(currentIndex).style);
                                }
                            });
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "网络请求失败", e);
            }
        });
    }

    private String parseBiliStreamUrl(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            if (json.getInt("code") != 0) return null;
            JSONObject data = json.getJSONObject("data");
            JSONObject playurlInfo = data.getJSONObject("playurl_info");
            JSONObject playurl = playurlInfo.getJSONObject("playurl");
            JSONArray streams = playurl.getJSONArray("stream");

            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                String protocolName = stream.getString("protocol_name");
                if ("http_hls".equals(protocolName) || "http_stream".equals(protocolName)) {
                    JSONArray formats = stream.getJSONArray("format");
                    for (int j = 0; j < formats.length(); j++) {
                        JSONObject format = formats.getJSONObject(j);
                        JSONArray codecs = format.getJSONArray("codec");
                        if (codecs.length() > 0) {
                            JSONObject codec = codecs.getJSONObject(0);
                            String baseUrl = codec.getString("base_url");
                            JSONArray urlInfo = codec.getJSONArray("url_info");
                            if (urlInfo.length() > 0) {
                                JSONObject info = urlInfo.getJSONObject(0);
                                return info.getString("host") + baseUrl + info.getString("extra");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON 解析异常", e);
        }
        return null;
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "LofiMusicSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        MediaMetadataCompat initialMetadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "LofiMusic")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "网络电台")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "LofiMusic Radio")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                .build();
        mediaSession.setMetadata(initialMetadata);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { super.onPlay(); requestAudioFocus(); startPlayback(); }
            @Override
            public void onPause() { super.onPause(); pausePlayback(); }
            @Override
            public void onSkipToPrevious() { super.onSkipToPrevious(); playPrevious(); }
            @Override
            public void onSkipToNext() { super.onSkipToNext(); playNext(); }
            @Override
            public void onStop() { super.onStop(); abandonAudioFocus(); stopPlayback(); }
        });
        mediaSession.setActive(true);
    }

    private void updateMediaMetadata(String title, String artist) {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "LofiMusic Radio")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                .build();
        mediaSession.setMetadata(metadata);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent playPauseIntent = new Intent(this, AudioPlayerService.class);
        playPauseIntent.setAction(ACTION_TOGGLE_PLAY_PAUSE);
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        boolean isPlaying = exoPlayer != null && exoPlayer.isPlaying();
        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String actionText = isPlaying ? "pause" : "play";

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action.Builder(playPauseIcon, actionText, playPausePendingIntent).build();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(stationList != null && currentIndex < stationList.size() ? stationList.get(currentIndex).name : "LofiMusic")
                .setContentText(stationList != null && currentIndex < stationList.size() ? stationList.get(currentIndex).style : "preparing...")
                .setSubText("LofiMusic Radio")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(android.R.drawable.ic_media_previous, "prev", getPendingIntentForAction(ACTION_PREV))
                .addAction(playPauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", getPendingIntentForAction(ACTION_NEXT))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(getPendingIntentForAction(ACTION_STOP)))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private PendingIntent getPendingIntentForAction(String action) {
        Intent intent = new Intent(this, AudioPlayerService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "音频播放控制", NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.enableLights(false);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LofiMusic::WakeLock");
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    public void startPlayback() {
        if (exoPlayer != null) {
            requestAudioFocus();
            exoPlayer.play();
        }
    }

    public void pausePlayback() { if (exoPlayer != null) exoPlayer.pause(); }

    public void togglePlayPause() {
        if (exoPlayer != null) {
            if (exoPlayer.isPlaying()) pausePlayback();
            else startPlayback();
        }
    }

    public boolean isPlaying() { return exoPlayer != null && exoPlayer.isPlaying(); }

    public void stopPlayback() {
        abandonAudioFocus();
        if (exoPlayer != null) { exoPlayer.stop(); exoPlayer.release(); exoPlayer = null; }
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); mediaSession = null; }
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void updateMediaSessionState(int state) {
        if (mediaSession != null) {
            long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_STOP;
            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPlayback();
        if (networkExecutor != null) networkExecutor.shutdown();
    }
}