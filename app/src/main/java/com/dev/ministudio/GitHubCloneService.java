package com.dev.ministudio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ProgressMonitor;
import java.io.File;
import android.net.Uri;


public class GitHubCloneService extends Service {
    private static final String CHANNEL_ID = "github_clone_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int NOTIFICATION_FINAL_ID = 2002;
    public static final String ACTION_CANCEL = "com.dev.ministudio.ACTION_CANCEL_CLONE";
    public static final String ACTION_CLONE_COMPLETE = "com.dev.ministudio.ACTION_CLONE_COMPLETE";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private volatile boolean isCancelled = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            isCancelled = true;
            showFinalNotification("🚫 ยกเลิกการ Clone เรียบร้อยแล้ว", true);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String githubUrl = intent.getStringExtra("githubUrl");
        String projectName = intent.getStringExtra("projectName");

        isCancelled = false;

        // ปุ่มยกเลิก
        Intent cancelIntent = new Intent(this, GitHubCloneService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent pendingCancelIntent = PendingIntent.getService(
                this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📥 MiniStudio: กำลัง Clone " + projectName)
                .setContentText("กำลังเริ่มการเชื่อมต่อ... 0%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ยกเลิก", pendingCancelIntent);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        new Thread(() -> {
            File targetDir = new File("/sdcard/MiniStudio/" + projectName);
            try {
                if (isCancelled) return;

                Git.cloneRepository()
                        .setURI(githubUrl)
                        .setDirectory(targetDir)
                        .setCloneSubmodules(true)
                        .setProgressMonitor(new ProgressMonitor() {
                            private int totalTasks = 0;
                            private int completed = 0;

                            @Override
                            public void start(int totalTasks) {}

                            @Override
                            public void beginTask(String title, int totalWork) {
                                this.totalTasks = totalWork;
                                this.completed = 0;
                                if (!isCancelled) {
                                    updateProgress(title + " 0%", 0);
                                }
                            }

                            @Override
                            public void update(int completedWork) {
                                this.completed += completedWork;
                                if (totalTasks > 0 && !isCancelled) {
                                    int percent = (int) ((completed / (float) totalTasks) * 100);
                                    updateProgress("กำลังดาวน์โหลด... " + percent + "%", percent);
                                }
                            }

                            @Override
                            public void endTask() {}

                            @Override
                            public boolean isCancelled() {
                                return isCancelled;
                            }

                            @Override
                            public void showDuration(boolean enabled) {}
                        })
                        .call();

                if (!isCancelled) {
                    showFinalNotification("🎉 Clone โปรเจกต์ " + projectName + " สำเร็จ!", false);
                    // ส่ง Broadcast แจ้งให้ Activity อัปเดตรายการโปรเจกต์
                    sendBroadcast(new Intent(ACTION_CLONE_COMPLETE));
                }

            } catch (Exception e) {
                if (!isCancelled) {
                    e.printStackTrace();
                    // ถ้าพัง ลบโฟลเดอร์ขยะออก
                    deleteRecursive(targetDir);
                    showFinalNotification("❌ Clone ล้มเหลว: " + e.getMessage(), true);
                }
            } finally {
                stopForeground(true);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void updateProgress(String text, int percent) {
        if (isCancelled) return;
        notificationBuilder.setContentText(text)
                           .setProgress(100, percent, false);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

private void showFinalNotification(String resultText, boolean isError) {
    NotificationCompat.Builder finalBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(isError ? "❌ MiniStudio: บิวด์/โหลด ล้มเหลว" : "✅ MiniStudio: ดาวน์โหลด APK เสร็จสมบูรณ์")
            .setContentText(resultText)
            .setSmallIcon(isError ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true) // แตะแล้วซ่อนการแจ้งเตือนอัตโนมัติ
            .setProgress(0, 0, false);

    // ถ้าทำงานสำเร็จ ให้สร้าง Intent สำหรับเรียกหน้าติดตั้ง APK
    if (!isError) {
        try {
            // 📍 ระบุ File Path ของไฟล์ APK ที่ดาวน์โหลดมาไว้ (ปรับตาม Path จริงที่น้าเซฟไฟล์นะครับ)
            File apkFile = new File("/sdcard/MiniStudio/app-release.apk");

            if (apkFile.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri apkUri;

                // รองรับ Android 7.0 (API 24) ขึ้นไป
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    apkUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            apkFile
                    );
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    apkUri = Uri.fromFile(apkFile);
                }

                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // สร้าง PendingIntent สำหรับผูกกับ Notification
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
                finalBuilder.setContentIntent(pendingIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    notificationManager.notify(NOTIFICATION_FINAL_ID, finalBuilder.build());
}
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GitHub Clone Progress",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("แสดงความคืบหน้าการ Clone โปรเจกต์จาก GitHub");
            channel.setSound(null, null);
            channel.enableVibration(false);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory != null && fileOrDirectory.exists()) {
            if (fileOrDirectory.isDirectory()) {
                File[] children = fileOrDirectory.listFiles();
                if (children != null) {
                    for (File child : children) deleteRecursive(child);
                }
            }
            fileOrDirectory.delete();
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
