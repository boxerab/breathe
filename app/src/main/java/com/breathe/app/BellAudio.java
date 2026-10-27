package com.breathe.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;

public final class BellAudio {
    public static final int ZHADA = 0;
    public static final int BASU = 1;
    public static final int DENGZE = 2;
    public static final int GONG_HANCHI = 3;
    public static final int KANGSE = 4;
    public static final int OMBU = 5;
    public static final int SAKYA = 6;
    public static final int SHURONG = 7;
    public static final int WOOD_BLOCK = 8;
    public static final String[] NAMES = {
        "Zhada", "Basu", "Dengze", "Gong Hanchi", "Kangse", "Ombu", "Sakya",
        "Shurong", "Wood block"
    };
    private static final int[] RESOURCES = {
        R.raw.zhada_bowl, R.raw.basu_bowl, R.raw.dengze_bowl, R.raw.gong_hanchi,
        R.raw.kangse_bowl, R.raw.ombu_bowl, R.raw.sakya_bowl, R.raw.shurong_bowl,
        R.raw.wood_block
    };
    private static final long STRIKE_SPACING_MILLISECONDS = 1900L;
    private static final long STRIKE_FADE_DURATION_MILLISECONDS = 1600L;
    private static final int STRIKE_FADE_STEPS = 32;
    private static final List<MediaPlayer> sessionPlayers = new ArrayList<>();
    private static final List<MediaPlayer> previewPlayers = new ArrayList<>();

    private BellAudio() { }

    public static void playStrikes(Context context, int tone, int strikes, Handler handler) {
        scheduleStrikes(context, tone, strikes, handler, sessionPlayers);
    }

    public static void playPreviewStrikes(Context context, int tone, int strikes, Handler handler) {
        cancelPreview(handler);
        scheduleStrikes(context, tone, strikes, handler, previewPlayers);
    }

    public static synchronized void cancelPreview(Handler handler) {
        handler.removeCallbacksAndMessages(null);
        for (MediaPlayer player : new ArrayList<>(previewPlayers)) release(previewPlayers, player);
    }

    private static void scheduleStrikes(Context context, int tone, int strikes, Handler handler,
            List<MediaPlayer> players) {
        if (strikes < 1) throw new IllegalArgumentException("Invalid strike count");
        MediaPlayer[] previousStrike = { null };
        for (int strike = 0; strike < strikes; strike++) {
            int strikeIndex = strike;
            handler.postDelayed(() -> {
                release(players, previousStrike[0]);
                previousStrike[0] = start(context, tone, players);
                if (strikeIndex + 1 < strikes) {
                    fadeBeforeNextStrike(previousStrike[0], handler, players);
                }
            }, strikeIndex * STRIKE_SPACING_MILLISECONDS);
        }
    }

    private static void fadeBeforeNextStrike(MediaPlayer player, Handler handler,
            List<MediaPlayer> players) {
        long fadeStart = STRIKE_SPACING_MILLISECONDS - STRIKE_FADE_DURATION_MILLISECONDS;
        long fadeStepDuration = STRIKE_FADE_DURATION_MILLISECONDS / STRIKE_FADE_STEPS;
        for (int step = 1; step <= STRIKE_FADE_STEPS; step++) {
            float volume = 1.0f - (float) step / STRIKE_FADE_STEPS;
            handler.postDelayed(() -> setVolume(players, player, volume),
                fadeStart + step * fadeStepDuration);
        }
    }

    private static synchronized MediaPlayer start(Context context, int tone, List<MediaPlayer> players) {
        if (tone < 0 || tone >= RESOURCES.length) return null;
        MediaPlayer player = MediaPlayer.create(context.getApplicationContext(), RESOURCES[tone],
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(), 0);
        if (player == null) return null;
        players.add(player);
        player.setOnCompletionListener(completedPlayer -> release(players, completedPlayer));
        player.setOnErrorListener((failedPlayer, what, extra) -> {
            release(players, failedPlayer); return true;
        });
        player.start();
        return player;
    }

    private static synchronized void release(List<MediaPlayer> players, MediaPlayer player) {
        if (player == null || !players.remove(player)) return;
        player.release();
    }

    private static synchronized void setVolume(List<MediaPlayer> players, MediaPlayer player,
            float volume) {
        if (player == null || !players.contains(player)) return;
        player.setVolume(volume, volume);
    }
}
