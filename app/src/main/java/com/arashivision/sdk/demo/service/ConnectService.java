package com.arashivision.sdk.demo.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.arashivision.sdk.demo.R;
import com.arashivision.sdk.demo.ui.main.MainActivity;

public class ConnectService extends Service {

    private static final String CHANNEL_ID = "FOREGROUND_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundService(); // 启动即显示通知
    }

    // 创建通知渠道（Android 8.0+ 必须）
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "前台服务通知",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("服务运行时的常驻通知");
        channel.enableLights(false);
        channel.enableVibration(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    // 启动前台服务通知
    private void startForegroundService() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher) // 必须为透明背景的白色图标
                .setContentTitle("服务正在运行")
                .setContentText("点击回到主界面")
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true); // 常驻通知（不可滑动删除）

        startForeground(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }
}
