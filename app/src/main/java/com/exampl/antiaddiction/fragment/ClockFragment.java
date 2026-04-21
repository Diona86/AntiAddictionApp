package com.exampl.antiaddiction.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.exampl.antiaddiction.R;
import com.google.android.material.button.MaterialButton;

public class ClockFragment extends Fragment {

    private enum Mode { STOPWATCH, TIMER }

    private Mode mode = Mode.STOPWATCH;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MaterialButton btnModeStopwatch;
    private MaterialButton btnModeTimer;
    private MaterialButton btnStartPause;
    private MaterialButton btnReset;
    private TextView tvClockValue;
    private LinearLayout layoutTimerInput;
    private EditText etTimerMinutes;
    private EditText etTimerSeconds;

    private boolean stopwatchRunning = false;
    private long stopwatchAccumulatedMs = 0L;
    private long stopwatchStartRealtimeMs = 0L;

    private boolean timerRunning = false;
    private long timerRemainingMs = 0L;
    private long timerEndRealtimeMs = 0L;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            if (mode == Mode.STOPWATCH && stopwatchRunning) {
                updateStopwatchText();
                handler.postDelayed(this, 100L);
            } else if (mode == Mode.TIMER && timerRunning) {
                updateTimerText();
                if (timerRemainingMs > 0) {
                    handler.postDelayed(this, 200L);
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clock, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        bindActions();
        switchMode(Mode.STOPWATCH);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(ticker);
        super.onDestroyView();
    }

    private void bindViews(View view) {
        btnModeStopwatch = view.findViewById(R.id.btnModeStopwatch);
        btnModeTimer = view.findViewById(R.id.btnModeTimer);
        btnStartPause = view.findViewById(R.id.btnClockStartPause);
        btnReset = view.findViewById(R.id.btnClockReset);
        tvClockValue = view.findViewById(R.id.tvClockValue);
        layoutTimerInput = view.findViewById(R.id.layoutTimerInput);
        etTimerMinutes = view.findViewById(R.id.etTimerMinutes);
        etTimerSeconds = view.findViewById(R.id.etTimerSeconds);
    }

    private void bindActions() {
        btnModeStopwatch.setOnClickListener(v -> switchMode(Mode.STOPWATCH));
        btnModeTimer.setOnClickListener(v -> switchMode(Mode.TIMER));
        btnStartPause.setOnClickListener(v -> {
            if (mode == Mode.STOPWATCH) {
                toggleStopwatch();
            } else {
                toggleTimer();
            }
        });
        btnReset.setOnClickListener(v -> {
            if (mode == Mode.STOPWATCH) {
                resetStopwatch();
            } else {
                resetTimer();
            }
        });
    }

    private void switchMode(Mode target) {
        mode = target;
        boolean stopwatchMode = mode == Mode.STOPWATCH;
        layoutTimerInput.setVisibility(stopwatchMode ? View.GONE : View.VISIBLE);
        btnModeStopwatch.setEnabled(!stopwatchMode);
        btnModeTimer.setEnabled(stopwatchMode);
        handler.removeCallbacks(ticker);
        if (stopwatchMode) {
            updateStopwatchText();
            btnStartPause.setText(stopwatchRunning ? "暂停" : (stopwatchAccumulatedMs > 0 ? "继续" : "开始"));
        } else {
            if (timerRemainingMs <= 0) {
                timerRemainingMs = parseTimerInputMs();
            }
            updateTimerTextOnly();
            btnStartPause.setText(timerRunning ? "暂停" : (timerRemainingMs > 0 ? "继续" : "开始"));
        }
    }

    private void toggleStopwatch() {
        if (stopwatchRunning) {
            stopwatchAccumulatedMs += SystemClock.elapsedRealtime() - stopwatchStartRealtimeMs;
            stopwatchRunning = false;
            handler.removeCallbacks(ticker);
            btnStartPause.setText("继续");
            updateStopwatchText();
        } else {
            stopwatchStartRealtimeMs = SystemClock.elapsedRealtime();
            stopwatchRunning = true;
            btnStartPause.setText("暂停");
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    private void resetStopwatch() {
        stopwatchRunning = false;
        stopwatchAccumulatedMs = 0L;
        stopwatchStartRealtimeMs = 0L;
        handler.removeCallbacks(ticker);
        tvClockValue.setText(formatMsToClock(0L));
        btnStartPause.setText("开始");
    }

    private void updateStopwatchText() {
        long elapsed = stopwatchAccumulatedMs;
        if (stopwatchRunning) {
            elapsed += SystemClock.elapsedRealtime() - stopwatchStartRealtimeMs;
        }
        tvClockValue.setText(formatMsToClock(elapsed));
    }

    private void toggleTimer() {
        if (timerRunning) {
            timerRemainingMs = Math.max(0L, timerEndRealtimeMs - SystemClock.elapsedRealtime());
            timerRunning = false;
            handler.removeCallbacks(ticker);
            btnStartPause.setText(timerRemainingMs > 0 ? "继续" : "开始");
            updateTimerTextOnly();
            return;
        }

        if (timerRemainingMs <= 0) {
            timerRemainingMs = parseTimerInputMs();
        }
        if (timerRemainingMs <= 0) {
            Toast.makeText(getContext(), "请先输入有效的倒计时", Toast.LENGTH_SHORT).show();
            return;
        }

        timerEndRealtimeMs = SystemClock.elapsedRealtime() + timerRemainingMs;
        timerRunning = true;
        btnStartPause.setText("暂停");
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void resetTimer() {
        timerRunning = false;
        handler.removeCallbacks(ticker);
        timerRemainingMs = parseTimerInputMs();
        updateTimerTextOnly();
        btnStartPause.setText(timerRemainingMs > 0 ? "开始" : "开始");
    }

    private void updateTimerText() {
        timerRemainingMs = Math.max(0L, timerEndRealtimeMs - SystemClock.elapsedRealtime());
        tvClockValue.setText(formatMsToClock(timerRemainingMs));
        if (timerRemainingMs <= 0L) {
            timerRunning = false;
            btnStartPause.setText("开始");
            Toast.makeText(getContext(), "计时结束", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTimerTextOnly() {
        tvClockValue.setText(formatMsToClock(Math.max(0L, timerRemainingMs)));
    }

    private long parseTimerInputMs() {
        int minutes = safeParseInt(etTimerMinutes == null ? "" : etTimerMinutes.getText().toString().trim());
        int seconds = safeParseInt(etTimerSeconds == null ? "" : etTimerSeconds.getText().toString().trim());
        minutes = Math.max(0, minutes);
        seconds = Math.max(0, Math.min(59, seconds));
        return (minutes * 60L + seconds) * 1000L;
    }

    private int safeParseInt(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatMsToClock(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
