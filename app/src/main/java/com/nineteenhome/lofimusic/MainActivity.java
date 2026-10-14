package com.nineteenhome.lofimusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private AudioPlayerService playerService;
    private boolean isBound = false;
    private Button btnPlayPause, btnPrev, btnNext, btnExit;
    private Button btn15min, btn30min, btn45min, btn60min;
    private Spinner spinnerStations;
    private ImageView imageViewSlideshow;
    private TextView tvClock, tvDateWeek, tvLunar;
    private TextView tvTimerStatus;

    // ================= 横屏专属控件 =================
    private View controlButtonsContainer;
    private ImageView btnLandPlayPause;
    private final Handler hideControlsHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    // =================================================

    private List<RadioStation> stationList;
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;

    private boolean isRotating = false;

    private final int[] imageResIds = {
            R.drawable.img_0, R.drawable.img_1, R.drawable.img_2, R.drawable.img_3,
            R.drawable.img_4, R.drawable.img_5, R.drawable.img_6, R.drawable.img_7,
            R.drawable.img_8, R.drawable.img_9, R.drawable.img_10, R.drawable.img_11,
            R.drawable.img_12, R.drawable.img_13, R.drawable.img_14, R.drawable.img_15,
            R.drawable.img_16, R.drawable.img_17, R.drawable.img_18, R.drawable.img_19,
            R.drawable.img_20, R.drawable.img_21, R.drawable.img_22, R.drawable.img_23,
            R.drawable.img_24, R.drawable.img_25, R.drawable.img_26, R.drawable.img_27,
            R.drawable.img_28, R.drawable.img_29, R.drawable.img_30
    };
    private int currentImageIndex = 0;
    private final Handler imageHandler = new Handler(Looper.getMainLooper());
    private Runnable imageRunnable;
    private final Random random = new Random();

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
    private final SimpleDateFormat weekFormat = new SimpleDateFormat("EEEE", Locale.CHINA);

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private int remainingSeconds = 0;
    private boolean isTimerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            isRotating = savedInstanceState.getBoolean("is_rotating", false);
            currentImageIndex = savedInstanceState.getInt("current_image_index", 0);
        }

        setContentView(R.layout.activity_main);

        // ================= 状态栏控制逻辑 =================
        Window window = getWindow();
        //noinspection deprecation
        window.setStatusBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        // 【修改】：无论横屏还是竖屏，都隐藏状态栏，实现真·全屏沉浸式体验
        insetsController.hide(WindowInsetsCompat.Type.statusBars());
        // 允许用户从屏幕顶部向下滑动时，临时呼出状态栏（松手后自动隐藏）
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // ===================================================

        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnExit = findViewById(R.id.btn_exit);
        btn15min = findViewById(R.id.btn_15min);
        btn30min = findViewById(R.id.btn_30min);
        btn45min = findViewById(R.id.btn_45min);
        btn60min = findViewById(R.id.btn_60min);
        spinnerStations = findViewById(R.id.spinner_stations);
        imageViewSlideshow = findViewById(R.id.image_view_slideshow);
        tvClock = findViewById(R.id.tv_clock);
        tvDateWeek = findViewById(R.id.tv_date_week);
        tvLunar = findViewById(R.id.tv_lunar);
        tvTimerStatus = findViewById(R.id.tv_timer_status);

        if (imageViewSlideshow != null) {
            imageViewSlideshow.setImageResource(imageResIds[currentImageIndex]);
        }

        View rootViewLand = findViewById(R.id.root_layout_land);
        if (rootViewLand != null) {
            controlButtonsContainer = findViewById(R.id.control_buttons_container);
            ImageView btnLandStation = findViewById(R.id.btn_land_station);
            btnLandPlayPause = findViewById(R.id.btn_land_play_pause);

            rootViewLand.setOnClickListener(v -> showControls());
            if (btnLandStation != null) {
                btnLandStation.setOnClickListener(v -> showStationSelector());
            }
            if (btnLandPlayPause != null) {
                btnLandPlayPause.setOnClickListener(v -> {
                    if (playerService != null) playerService.togglePlayPause();
                });
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }

        initRadioStations();

        if (spinnerStations != null) setupSpinner();
        if (btnExit != null) setupButtons();
        if (btn15min != null) setupTimerButtons();

        initSlideshow();
        startClock();
        startAndBindService();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_rotating", true);
        outState.putInt("current_image_index", currentImageIndex);
    }

    private void showControls() {
        if (controlButtonsContainer != null) {
            controlButtonsContainer.setVisibility(View.VISIBLE);
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
            hideControlsRunnable = () -> {
                if (controlButtonsContainer != null) {
                    controlButtonsContainer.setVisibility(View.GONE);
                }
            };
            hideControlsHandler.postDelayed(hideControlsRunnable, 2400);
        }
    }

    private void showStationSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Station");

        List<RadioStation> currentList = (playerService != null && playerService.getStationList() != null)
                ? playerService.getStationList() : stationList;

        String[] stationNames = new String[currentList.size()];
        for (int i = 0; i < currentList.size(); i++) {
            stationNames[i] = currentList.get(i).name;
        }

        int currentIndex = playerService != null ? playerService.getCurrentIndex() : 0;

        builder.setSingleChoiceItems(stationNames, currentIndex, (dialog, which) -> {
            if (playerService != null && playerService.getCurrentIndex() != which) {
                playerService.playStationAt(which);
            }
            dialog.dismiss();
            showControls();
        });

        builder.show();
    }

    private void setupTimerButtons() {
        btn15min.setOnClickListener(v -> startTimer(15));
        btn30min.setOnClickListener(v -> startTimer(30));
        btn45min.setOnClickListener(v -> startTimer(45));
        btn60min.setOnClickListener(v -> startTimer(60));
    }

    private void startTimer(int minutes) {
        cancelTimer();
        remainingSeconds = minutes * 60;
        isTimerRunning = true;
        updateTimerUI();
        Toast.makeText(this, minutes + " min to Quit", Toast.LENGTH_SHORT).show();

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (remainingSeconds > 0) {
                    remainingSeconds--;
                    updateTimerUI();
                    timerHandler.postDelayed(this, 1000);
                } else {
                    Toast.makeText(MainActivity.this, "Quitting...", Toast.LENGTH_SHORT).show();
                    autoExitApp();
                }
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void cancelTimer() {
        isTimerRunning = false;
        remainingSeconds = 0;
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        updateTimerUI();
    }

    @SuppressLint("SetTextI18n")
    private void updateTimerUI() {
        if (tvTimerStatus == null) return;
        if (isTimerRunning) {
            int mins = remainingSeconds / 60;
            int secs = remainingSeconds % 60;
            tvTimerStatus.setText(String.format(Locale.getDefault(), "Quit After %02d:%02d", mins, secs));
            tvTimerStatus.setTextColor(0xFF757575);//0xFF4CAF50
        } else {
            tvTimerStatus.setText("Unset");
            tvTimerStatus.setTextColor(0xFF757575);
        }
    }

    private void autoExitApp() {
        cancelTimer();
        if (playerService != null) playerService.stopPlayback();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        finishAndRemoveTask();
    }

    private void startClock() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                updateClockAndDate();
                clockHandler.postDelayed(this, 1000);
            }
        };
        updateClockAndDate();
        clockHandler.post(clockRunnable);
    }

    private void stopClock() {
        if (clockHandler != null && clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateClockAndDate() {
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();

        if (tvClock != null) tvClock.setText(timeFormat.format(now));
        if (tvDateWeek != null) {
            tvDateWeek.setText(dateFormat.format(now) + " " + weekFormat.format(now));
        }
        if (tvLunar != null) {
            LunarCalendar lunar = new LunarCalendar(calendar);
            tvLunar.setText(lunar.getLunarYear() + "（" + lunar.getAnimalYear() + "） " + lunar.getLunarMonth() + lunar.getLunarDay());
        }
    }

    private void initSlideshow() {
        imageRunnable = new Runnable() {
            @Override
            public void run() {
                currentImageIndex = (currentImageIndex + 1) % imageResIds.length;
                if (imageViewSlideshow != null) {
                    imageViewSlideshow.setImageResource(imageResIds[currentImageIndex]);
                }
                imageHandler.postDelayed(this, getNextRandomInterval());
            }
        };
    }

    private long getNextRandomInterval() {
        return random.nextInt(16001) + 3000;
    }

    private void startSlideshow() {
        imageHandler.removeCallbacks(imageRunnable);
        imageHandler.postDelayed(imageRunnable, getNextRandomInterval());
    }

    private void stopSlideshow() {
        imageHandler.removeCallbacks(imageRunnable);
    }

    private void initRadioStations() {
        // 传入 this (即 Context) 来读取 JSON 文件
        stationList = RadioStationRepository.getStations(this);
    }

    private void setupSpinner() {
        List<String> names = new ArrayList<>();
        for (RadioStation s : stationList) names.add(s.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStations.setAdapter(adapter);

        spinnerStations.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (playerService != null && playerService.getCurrentIndex() != position) {
                    playerService.playStationAt(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupButtons() {
        btnPlayPause.setOnClickListener(v -> {
            if (playerService != null) playerService.togglePlayPause();
        });
        btnPrev.setOnClickListener(v -> {
            if (playerService != null) playerService.playPrevious();
        });
        btnNext.setOnClickListener(v -> {
            if (playerService != null) playerService.playNext();
        });
        btnExit.setOnClickListener(v -> {
            cancelTimer();
            stopSlideshow();
            stopClock();
            if (playerService != null) playerService.stopPlayback();
            if (isBound) {
                unbindService(serviceConnection);
                isBound = false;
            }
            finishAndRemoveTask();
        });
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, AudioPlayerService.class);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlayerService.LocalBinder binder = (AudioPlayerService.LocalBinder) service;
            playerService = binder.getService();
            isBound = true;

            if (playerService.getStationList() == null) {
                playerService.setStationList(stationList, 0);
            }

            int currentIndex = playerService.getCurrentIndex();

            playerService.setStateListener(new AudioPlayerService.PlaybackStateListener() {
                @Override
                public void onPlaybackStateChanged(boolean isPlaying) {
                    runOnUiThread(() -> {
                        if (btnPlayPause != null) {
                            btnPlayPause.setText(isPlaying ? "PAUSE" : "PLAY");
                        }
                        updateLandPlayPauseIcon(isPlaying);
                        if (isPlaying) startSlideshow();
                        else stopSlideshow();
                    });
                }

                @Override
                public void onStationChanged(int index) {
                    runOnUiThread(() -> {
                        if (spinnerStations != null) spinnerStations.setSelection(index, false);
                        updateNavButtons(index);
                        currentImageIndex = 0;
                        if (imageViewSlideshow != null)
                            imageViewSlideshow.setImageResource(imageResIds[0]);
                        if (playerService != null && playerService.isPlaying()) startSlideshow();
                    });
                }
            });

            updateNavButtons(currentIndex);
            if (btnPlayPause != null) {
                btnPlayPause.setText(playerService.isPlaying() ? "PAUSE" : "PLAY");
            }
            updateLandPlayPauseIcon(playerService.isPlaying());

            if (spinnerStations != null) {
                spinnerStations.setSelection(currentIndex, false);
            }

            if (!isRotating && !playerService.isPlaying()) {
                playerService.playStationAt(currentIndex);
            }

            if (playerService.isPlaying()) {
                startSlideshow();
            } else {
                stopSlideshow();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            playerService = null;
        }
    };

    private void updateLandPlayPauseIcon(boolean isPlaying) {
        if (btnLandPlayPause != null) {
            // 使用系统标准图标，风格绝对统一
            btnLandPlayPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
    }

    private void updateNavButtons(int currentIndex) {
        if (btnPrev != null) {
            btnPrev.setEnabled(currentIndex > 0);
            btnPrev.setAlpha(currentIndex > 0 ? 1.0f : 0.5f);
        }
        if (btnNext != null) {
            btnNext.setEnabled(currentIndex < stationList.size() - 1);
            btnNext.setAlpha(currentIndex < stationList.size() - 1 ? 1.0f : 0.5f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlideshow();
        stopClock();
        cancelTimer();

        if (hideControlsHandler != null && hideControlsRunnable != null) {
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
        }

        if (!isChangingConfigurations()) {
            if (playerService != null) playerService.stopPlayback();
        }

        if (isBound) {
            if (playerService != null) playerService.setStateListener(null);
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}