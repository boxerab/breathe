package com.breathe.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BellEvent {
    public static final int MINIMUM_STRIKES = 1;
    public static final int MAXIMUM_STRIKES = 5;
    public final int remainingSeconds;
    public final int sound;
    public final int strikes;

    public BellEvent(int remainingSeconds, int sound, int strikes) {
        if (remainingSeconds < 0 || sound < 0 || sound >= BellAudio.NAMES.length
                || strikes < MINIMUM_STRIKES || strikes > MAXIMUM_STRIKES) {
            throw new IllegalArgumentException("Invalid bell event");
        }
        this.remainingSeconds = remainingSeconds;
        this.sound = sound;
        this.strikes = strikes;
    }

    public static List<BellEvent> ordered(List<BellEvent> events, int durationSeconds) {
        List<BellEvent> result = new ArrayList<>();
        for (BellEvent e : events) {
            if (e.remainingSeconds <= durationSeconds) result.add(e);
        }
        result.sort(Comparator.comparingInt((BellEvent e) -> e.remainingSeconds).reversed());
        return result;
    }
}
