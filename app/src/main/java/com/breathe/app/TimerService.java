package com.breathe.app;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class TimerService extends Service {
    public static final String ACTION_START = "com.breathe.app.START";
    public static final String ACTION_STOP = "com.breathe.app.STOP";
    public static final String ACTION_TICK = "com.breathe.app.TICK";
    public static final String EXTRA_REMAINING = "remaining";
    public static final String EXTRA_ACTIVE = "active";
    private static final String CHANNEL = "meditation";
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int OPEN_APP_REQUEST_CODE = 1;
    private static final int STOP_SESSION_REQUEST_CODE = 2;
    private static final long TICK_INTERVAL_MILLISECONDS = 250L;
    private static final long ENDING_STRIKE_GRACE_SECONDS = 10L;
    private static final long WAKE_LOCK_GRACE_SECONDS = 60L;
    private static final long MILLISECONDS_PER_SECOND = TimeUnit.SECONDS.toMillis(1L);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BellEvent> events = new ArrayList<>();
    private PowerManager.WakeLock wakeLock;
    private long beganAt;
    private long endsAtWallClockMilliseconds;
    private int durationSeconds;
    private int nextEvent;
    private boolean active;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Meditation timer",
            NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return active ? START_STICKY : START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) { stopSession(); return START_NOT_STICKY; }
        if (ACTION_START.equals(intent.getAction())) {
            if (active) stopSession();
            Settings settings = Settings.load(this);
            durationSeconds = settings.durationSeconds();
            events.clear();
            events.addAll(settings.schedule());
            beganAt = SystemClock.elapsedRealtime();
            endsAtWallClockMilliseconds = System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(durationSeconds);
            nextEvent = 0;
            active = true;
            startForeground(FOREGROUND_NOTIFICATION_ID, notification());
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Breathe:meditation");
            wakeLock.acquire(TimeUnit.SECONDS.toMillis(durationSeconds + WAKE_LOCK_GRACE_SECONDS));
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
        return START_NOT_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            long elapsedMilliseconds = SystemClock.elapsedRealtime() - beganAt;
            while (nextEvent < events.size()
                    && elapsedMilliseconds >= (durationSeconds - events.get(nextEvent).remainingSeconds)
                        * MILLISECONDS_PER_SECOND) {
                BellEvent event = events.get(nextEvent++);
                BellAudio.playStrikes(TimerService.this, event.sound, event.strikes, handler);
            }
            int remaining = Math.max(0, durationSeconds
                - Math.toIntExact(elapsedMilliseconds / MILLISECONDS_PER_SECOND));
            Intent update = new Intent(ACTION_TICK).setPackage(getPackageName());
            update.putExtra(EXTRA_REMAINING, remaining).putExtra(EXTRA_ACTIVE, true);
            sendBroadcast(update);
            if (elapsedMilliseconds >= TimeUnit.SECONDS.toMillis(durationSeconds)) {
                // Allow scheduled ending strikes to ring after the session completes.
                handler.postDelayed(() -> stopSession(),
                    TimeUnit.SECONDS.toMillis(ENDING_STRIKE_GRACE_SECONDS));
                active = false;
                broadcastStopped();
            } else {
                handler.postDelayed(this, TICK_INTERVAL_MILLISECONDS);
            }
        }
    };

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, OPEN_APP_REQUEST_CODE, open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, TimerService.class).setAction(ACTION_STOP);
        PendingIntent cancel = PendingIntent.getService(this, STOP_SESSION_REQUEST_CODE, stop,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Breathe meditation")
            .setContentText("Time remaining").setWhen(endsAtWallClockMilliseconds)
            .setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(content)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", cancel).build();
    }

    private void broadcastStopped() {
        Intent update = new Intent(ACTION_TICK).setPackage(getPackageName());
        update.putExtra(EXTRA_REMAINING, 0).putExtra(EXTRA_ACTIVE, false);
        sendBroadcast(update);
    }

    private void stopSession() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        broadcastStopped();
        stopSelf();
    }

    @Override public void onDestroy() { stopSession(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
