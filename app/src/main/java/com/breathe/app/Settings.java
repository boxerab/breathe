package com.breathe.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Settings {
    private static final int TONE_LIST_VERSION = 1;
    private static final int ELAPSED_BELL_TIMING_VERSION = 1;
    private static final int BELL_TIMING_VERSION = 2;
    private static final int DEFAULT_DURATION_MINUTES = 20;
    public static final int MINIMUM_DURATION_MINUTES = 1;
    public static final int MAXIMUM_DURATION_MINUTES = 180;
    private static final int DEFAULT_END_STRIKES = 3;
    public int durationMinutes = DEFAULT_DURATION_MINUTES;
    public int startSound = BellAudio.BASU;
    public int startStrikes = BellEvent.MINIMUM_STRIKES;
    public int endSound = BellAudio.SAKYA;
    public int endStrikes = DEFAULT_END_STRIKES;
    public final List<BellEvent> middle = new ArrayList<>();

    public static Settings load(Context context) {
        Settings settings = new Settings();
        SharedPreferences preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE);
        boolean hasLegacyToneSettings = !preferences.contains("toneListVersion")
            && (preferences.contains("startSound") || preferences.contains("endSound") || preferences.contains("middle"));
        settings.durationMinutes = clamp(preferences.getInt("duration", DEFAULT_DURATION_MINUTES),
            MINIMUM_DURATION_MINUTES, MAXIMUM_DURATION_MINUTES);
        int startSound = preferences.getInt("startSound", BellAudio.BASU);
        int endSound = preferences.getInt("endSound", BellAudio.SAKYA);
        if (hasLegacyToneSettings) {
            startSound = migrateLegacyTone(startSound);
            endSound = migrateLegacyTone(endSound);
        }
        settings.startSound = clamp(startSound, 0, BellAudio.NAMES.length - 1);
        settings.startStrikes = clamp(preferences.getInt("startStrikes", BellEvent.MINIMUM_STRIKES),
            BellEvent.MINIMUM_STRIKES, BellEvent.MAXIMUM_STRIKES);
        settings.endSound = clamp(endSound, 0, BellAudio.NAMES.length - 1);
        settings.endStrikes = clamp(preferences.getInt("endStrikes", DEFAULT_END_STRIKES),
            BellEvent.MINIMUM_STRIKES, BellEvent.MAXIMUM_STRIKES);
        int storedBellTimingVersion = preferences.getInt("bellTimingVersion", 0);
        boolean migratedBellTiming = false;
        try {
            JSONArray items = new JSONArray(preferences.getString("middle", "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                int remainingSeconds;
                if (item.has("remainingSeconds")) {
                    remainingSeconds = item.getInt("remainingSeconds");
                    if (storedBellTimingVersion == ELAPSED_BELL_TIMING_VERSION) {
                        remainingSeconds = settings.durationSeconds() - remainingSeconds;
                        migratedBellTiming = true;
                    }
                } else if (item.has("offsetSeconds")) {
                    remainingSeconds = item.getInt("offsetSeconds");
                    migratedBellTiming = true;
                } else {
                    remainingSeconds = Math.toIntExact(
                        TimeUnit.MINUTES.toSeconds(item.getInt("minute")));
                    migratedBellTiming = true;
                }
                int sound = item.getInt("sound");
                if (hasLegacyToneSettings) sound = migrateLegacyTone(sound);
                if (remainingSeconds >= 0 && remainingSeconds <= settings.durationSeconds()) {
                    settings.middle.add(new BellEvent(remainingSeconds, sound, item.getInt("strikes")));
                }
            }
        } catch (Exception ignored) {
            settings.middle.clear();
        }
        if (hasLegacyToneSettings || migratedBellTiming) settings.save(context);
        return settings;
    }

    private static int migrateLegacyTone(int tone) {
        switch (tone) {
            case 0: return BellAudio.BASU;
            case 1: return BellAudio.SAKYA;
            case 2: return BellAudio.WOOD_BLOCK;
            case 3: return BellAudio.OMBU;
            case 4: return BellAudio.KANGSE;
            default: throw new IllegalArgumentException("Invalid legacy tone");
        }
    }

    public void save(Context context) {
        JSONArray items = new JSONArray();
        for (BellEvent event : middle) {
            JSONObject item = new JSONObject();
            try {
                item.put("remainingSeconds", event.remainingSeconds);
                item.put("sound", event.sound);
                item.put("strikes", event.strikes);
                items.put(item);
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit()
            .putInt("duration", durationMinutes)
            .putInt("startSound", startSound).putInt("startStrikes", startStrikes)
            .putInt("endSound", endSound).putInt("endStrikes", endStrikes)
            .putString("middle", items.toString())
            .putInt("toneListVersion", TONE_LIST_VERSION)
            .putInt("bellTimingVersion", BELL_TIMING_VERSION).apply();
    }

    public List<BellEvent> schedule() {
        List<BellEvent> items = new ArrayList<>();
        items.add(new BellEvent(durationSeconds(), startSound, startStrikes));
        items.addAll(middle);
        items.add(new BellEvent(0, endSound, endStrikes));
        return BellEvent.ordered(items, durationSeconds());
    }

    public int durationSeconds() {
        return Math.toIntExact(TimeUnit.MINUTES.toSeconds(durationMinutes));
    }

    public static int clamp(int value, int min, int max) { return Math.min(max, Math.max(min, value)); }
}
