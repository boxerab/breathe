package com.breathe.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1;
    private static final int START_EVENT_INDEX = -1;
    private static final int FINISH_EVENT_INDEX = -2;
    private static final int ADD_EVENT_INDEX = -3;
    private static final int SESSION_MIDPOINT_DIVISOR = 2;
    private static final int SETUP_CLOCK_TEXT_SIZE_SP = 52;
    private static final int SESSION_CLOCK_TEXT_SIZE_SP = 96;
    private static final float SESSION_SCREEN_BRIGHTNESS = 0.05f;
    private static final long PREVIEW_DELAY_MILLISECONDS = 500L;
    private static final long INITIAL_PREVIEW_DELAY_MILLISECONDS = 150L;
    private Settings settings;
    private LinearLayout root;
    private TextView clock;
    private Button startButton;
    private boolean running;
    private final Handler previewHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean wasRunning = running;
            running = intent.getBooleanExtra(TimerService.EXTRA_ACTIVE, false);
            int remaining = intent.getIntExtra(TimerService.EXTRA_REMAINING,
                settings.durationSeconds());
            setSessionDisplayActive(running);
            if (wasRunning != running) {
                render();
                return;
            }
            if (clock != null) clock.setText(format(remaining));
            if (startButton != null) startButton.setText(running ? "Stop session" : "Start session");
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18, 26, 27));
        getWindow().setNavigationBarColor(Color.rgb(18, 26, 27));
        settings = Settings.load(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                NOTIFICATION_PERMISSION_REQUEST_CODE);
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter(TimerService.ACTION_TICK),
            Context.RECEIVER_NOT_EXPORTED);
    }
    @Override protected void onStop() { unregisterReceiver(receiver); super.onStop(); }

    private void setSessionDisplayActive(boolean sessionActive) {
        int sessionWindowFlags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (sessionActive) getWindow().addFlags(sessionWindowFlags);
        else getWindow().clearFlags(sessionWindowFlags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(sessionActive);
        } else if (sessionActive) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        WindowManager.LayoutParams windowAttributes = getWindow().getAttributes();
        windowAttributes.screenBrightness = sessionActive
            ? SESSION_SCREEN_BRIGHTNESS : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(windowAttributes);
    }

    private static String format(int seconds) {
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        long secondWithinMinute = seconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secondWithinMinute);
    }
    private int toPixels(int densityIndependentPixels) {
        return (int)(densityIndependentPixels * getResources().getDisplayMetrics().density + 0.5f);
    }
    private TextView text(String value, int size) {
        TextView textView = new TextView(this);
        textView.setText(value); textView.setTextSize(size); textView.setTextColor(Color.rgb(224, 236, 229));
        textView.setPadding(toPixels(3), toPixels(8), toPixels(3), toPixels(8));
        return textView;
    }
    private Button button(String caption, Runnable click) {
        Button created = new Button(this); created.setText(caption); created.setAllCaps(false);
        created.setOnClickListener(view -> click.run()); return created;
    }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(running ? Color.BLACK : Color.rgb(18, 26, 27));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(toPixels(22), toPixels(24), toPixels(22), toPixels(40)); scroll.addView(root);
        setContentView(scroll);
        TextView title = text("Breathe", 32); root.addView(title);
        root.addView(text("A quiet, offline meditation timer", 14));
        clock = text(format(settings.durationSeconds()),
            running ? SESSION_CLOCK_TEXT_SIZE_SP : SETUP_CLOCK_TEXT_SIZE_SP);
        clock.setGravity(Gravity.CENTER); clock.setPadding(0, toPixels(25), 0, toPixels(20));
        root.addView(clock);
        startButton = button(running ? "Stop session" : "Start session", () -> {
            if (running) {
                startService(new Intent(this, TimerService.class).setAction(TimerService.ACTION_STOP));
                running = false;
                setSessionDisplayActive(false);
                render();
            } else {
                settings.save(this);
                Intent intent = new Intent(this, TimerService.class).setAction(TimerService.ACTION_START);
                startForegroundService(intent);
                running = true;
                setSessionDisplayActive(true);
                render();
            }
        });
        root.addView(startButton);
        if (running) return;
        root.addView(button("Duration: " + settings.durationMinutes + " minutes  ·  change", () -> {
            NumberPicker picker = picker(Settings.MINIMUM_DURATION_MINUTES,
                Settings.MAXIMUM_DURATION_MINUTES, settings.durationMinutes, null);
            new AlertDialog.Builder(this).setTitle("Session length (minutes)")
                .setView(wrapPicker(picker)).setPositiveButton("Save", (clickedDialog, which) -> {
                    settings.durationMinutes = picker.getValue();
                    settings.middle.removeIf(bell -> bell.remainingSeconds > settings.durationSeconds());
                    settings.save(this); render();
                }).setNegativeButton("Cancel", null).show();
        }));
        root.addView(text("Bells", 23));
        root.addView(text("Tap a bell to edit. Long-press an interval to delete it.", 13));
        root.addView(eventButton("Start", new BellEvent(settings.durationSeconds(),
            settings.startSound, settings.startStrikes), START_EVENT_INDEX));
        List<BellEvent> order = BellEvent.ordered(settings.middle, settings.durationSeconds());
        for (BellEvent bell : order) {
            final int index = settings.middle.indexOf(bell);
            Button bellButton = eventButton("At " + format(bell.remainingSeconds) + " remaining", bell, index);
            bellButton.setOnLongClickListener(view -> {
                new AlertDialog.Builder(this).setMessage("Remove this bell?")
                    .setPositiveButton("Remove", (clickedDialog, which) -> {
                        settings.middle.remove(bell); settings.save(this); render();
                    }).setNegativeButton("Cancel", null).show();
                return true;
            });
            root.addView(bellButton);
        }
        root.addView(eventButton("Finish", new BellEvent(0, settings.endSound,
            settings.endStrikes), FINISH_EVENT_INDEX));
        root.addView(button("+ Add bell at countdown time",
            () -> editEvent(null, ADD_EVENT_INDEX)));
        root.addView(text("Tone selection plays a preview immediately. Bells are stored on your phone; no account or internet required.", 13));
    }
    private Button eventButton(String position, BellEvent event, int index) {
        return button(position + "  ·  " + BellAudio.NAMES[event.sound] + "  ·  "
            + event.strikes + (event.strikes == BellEvent.MINIMUM_STRIKES
                ? " strike" : " strikes"),
            () -> editEvent(event, index));
    }
    private NumberPicker picker(int min, int max, int chosen, String[] labels) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min); picker.setMaxValue(max);
        if (labels != null) picker.setDisplayedValues(labels);
        picker.setValue(chosen); picker.setWrapSelectorWheel(false);
        return picker;
    }
    private LinearLayout wrapPicker(NumberPicker picker) {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER); layout.addView(picker);
        layout.setPadding(toPixels(12), toPixels(8), toPixels(12), toPixels(8));
        return layout;
    }
    private void editEvent(BellEvent existing, int index) {
        BellAudio.cancelPreview(previewHandler);
        int remainingSeconds = existing == null
            ? settings.durationSeconds() / SESSION_MIDPOINT_DIVISOR
            : existing.remainingSeconds;
        int tone = existing == null ? 0 : existing.sound;
        int strikes = existing == null ? 1 : existing.strikes;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(toPixels(20), toPixels(8), toPixels(20), 0);
        NumberPicker atMinute = null;
        NumberPicker atSecond = null;
        if (index >= 0 || index == ADD_EVENT_INDEX) {
            layout.addView(text("Minutes remaining", 16));
            atMinute = picker(0, settings.durationMinutes,
                Math.toIntExact(TimeUnit.SECONDS.toMinutes(remainingSeconds)), null);
            layout.addView(wrapPicker(atMinute));
            layout.addView(text("Seconds remaining", 16));
            int maximumSecond = atMinute.getValue() == settings.durationMinutes
                ? 0 : Math.toIntExact(TimeUnit.MINUTES.toSeconds(1) - 1);
            int secondWithinMinute = remainingSeconds
                - Math.toIntExact(TimeUnit.MINUTES.toSeconds(atMinute.getValue()));
            atSecond = picker(0, maximumSecond, secondWithinMinute, null);
            atSecond.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));
            NumberPicker secondPicker = atSecond;
            atMinute.setOnValueChangedListener((changedPicker, oldValue, newValue) -> {
                if (newValue == settings.durationMinutes) {
                    secondPicker.setValue(0);
                    secondPicker.setMaxValue(0);
                } else if (secondPicker.getMaxValue() == 0) {
                    secondPicker.setMaxValue(
                        Math.toIntExact(TimeUnit.MINUTES.toSeconds(1) - 1));
                }
            });
            layout.addView(wrapPicker(atSecond));
        }
        layout.addView(text("Bell tone (scroll to preview)", 16));
        NumberPicker tonePicker = picker(0, BellAudio.NAMES.length - 1, tone, BellAudio.NAMES);
        NumberPicker count = picker(BellEvent.MINIMUM_STRIKES, BellEvent.MAXIMUM_STRIKES,
            strikes, null);
        Runnable preview = () -> BellAudio.playPreviewStrikes(
            this, tonePicker.getValue(), count.getValue(), previewHandler);
        Runnable queuePreview = () -> {
            BellAudio.cancelPreview(previewHandler);
            previewHandler.postDelayed(preview, PREVIEW_DELAY_MILLISECONDS);
        };
        tonePicker.setOnValueChangedListener((changedPicker, oldValue, newValue) -> queuePreview.run());
        count.setOnValueChangedListener((changedPicker, oldValue, newValue) -> queuePreview.run());
        layout.addView(wrapPicker(tonePicker));
        layout.addView(button("▶ Preview selected strikes", preview));
        layout.addView(text("Number of strikes", 16));
        layout.addView(wrapPicker(count));
        ScrollView body = new ScrollView(this); body.addView(layout);
        final NumberPicker minutePicker = atMinute;
        final NumberPicker secondPicker = atSecond;
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(index == ADD_EVENT_INDEX ? "Add bell" : "Edit bell")
            .setView(body).setPositiveButton("Save", (clickedDialog, which) -> {
                int savedRemainingSeconds = minutePicker == null
                    ? (index == START_EVENT_INDEX ? settings.durationSeconds() : 0)
                    : Math.toIntExact(TimeUnit.MINUTES.toSeconds(minutePicker.getValue()))
                        + secondPicker.getValue();
                BellEvent updated = new BellEvent(savedRemainingSeconds, tonePicker.getValue(),
                    count.getValue());
                if (index == START_EVENT_INDEX) {
                    settings.startSound = updated.sound;
                    settings.startStrikes = updated.strikes;
                } else if (index == FINISH_EVENT_INDEX) {
                    settings.endSound = updated.sound;
                    settings.endStrikes = updated.strikes;
                } else if (index == ADD_EVENT_INDEX) settings.middle.add(updated);
                else settings.middle.set(index, updated);
                settings.save(this); render();
            }).setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(dismissedDialog -> BellAudio.cancelPreview(previewHandler));
        dialog.show();
        previewHandler.postDelayed(preview, INITIAL_PREVIEW_DELAY_MILLISECONDS);
    }
}
