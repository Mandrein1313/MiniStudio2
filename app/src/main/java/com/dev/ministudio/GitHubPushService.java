package com.dev.ministudio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.io.File;

public class GitHubPushService extends Service {
    private static final String CHANNEL_ID = "github_push_channel";
    private static final int NOTIFICATION_ID = 1001;      // ID สำหรับหลอดวิ่งขณะทำงาน
    private static final int NOTIFICATION_FINAL_ID = 1002;// ID สำหรับแสดงผลลัพธ์ค้างไว้
    
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String projectName = intent.getStringExtra("projectName");
        String username = intent.getStringExtra("username");
        String token = intent.getStringExtra("token");
        String repoUrl = intent.getStringExtra("repoUrl");

        // 🌟 ตั้งค่า Notification แบบเงียบ (ไม่มีเสียง) + แสดงสถานะเริ่มต้น
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚀 MiniStudio: " + (projectName != null ? projectName : "Pushing..."))
                .setContentText("กำลังเริ่มระบบ... 0%")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setPriority(NotificationCompat.PRIORITY_LOW) // ไร้เสียงเตือน
                .setOnlyAlertOnce(true)
                .setOngoing(true) // ป้องกันผู้ใช้เผลอลบระหว่างอัปโหลด
                .setProgress(100, 0, true);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        new Thread(() -> {
            File projectDir = new File("/sdcard/MiniStudio/" + projectName);
            try {
                Git git;
                updateStatus("[1/4] ตรวจสอบระบบ Git...", 0, true);
                if (!new File(projectDir, ".git").exists()) {
                    git = Git.init().setDirectory(projectDir).call();
                } else {
                    git = Git.open(projectDir);
                }

                updateStatus("[2/4] รวบรวมไฟล์ทั้งหมด...", 0, true);
                git.add().addFilepattern(".").call();

                updateStatus("[3/4] บันทึกประวัติ (Commit)...", 0, true);
                try {
                    git.commit().setMessage("Updated via MiniStudio - " + new java.util.Date()).call();
                } catch (Exception ce) {
                    // กรณีไม่มีไฟล์เปลี่ยนแปลงให้ Commit
                }

                git.getRepository().getConfig().setString("remote", "origin", "url", repoUrl);
                git.getRepository().getConfig().save();

                // 🌟 [4/4] Push พร้อมแสดง % และหลอดวิ่ง
                git.push()
                   .setRemote(repoUrl)
                   .setPushAll()
                   .setProgressMonitor(new ProgressMonitor() {
                       private int totalTasks = 0;
                       private int completed = 0;

                       @Override
                       public void start(int totalTasks) {}

                       @Override
                       public void beginTask(String title, int totalWork) {
                           this.totalTasks = totalWork;
                           this.completed = 0;
                           updateProgress("[4/4] " + title + " 0%", 0);
                       }

                       @Override
                       public void update(int completedWork) {
                           this.completed += completedWork;
                           if (totalTasks > 0) {
                               int percent = (int) ((completed / (float) totalTasks) * 100);
                               updateProgress("[4/4] กำลังอัปโหลด... " + percent + "%", percent);
                           }
                       }

                       @Override
                       public void endTask() {}

                       @Override
                       public boolean isCancelled() { return false; }

                       @Override
                       public void showDuration(boolean enabled) {}
                   })
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                   .call();

                // 🌟 แสดงแจ้งเตือนค้างไว้เมื่อสำเร็จ
                showFinalNotification("🎉 อัปโหลดขึ้น GitHub สำเร็จแล้วครับ!", false);

            } catch (Exception e) {
                e.printStackTrace();
                // 🌟 แสดงแจ้งเตือนค้างไว้เมื่อล้มเหลว
                showFinalNotification("❌ ล้มเหลว: " + e.getMessage(), true);
            } finally {
                // ปิด Service หลอดวิ่ง
                stopForeground(true); 
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void updateStatus(String text, int progress, boolean indeterminate) {
        notificationBuilder.setContentText(text)
                           .setProgress(100, progress, indeterminate);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateProgress(String text, int percent) {
        notificationBuilder.setContentText(text)
                           .setProgress(100, percent, false);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    // 🌟 ฟังก์ชันสร้าง Notification แสดงผลลัพธ์แบบ "ค้างไว้บนหน้าจอ"
    private void showFinalNotification(String resultText, boolean isError) {
        NotificationCompat.Builder finalBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(isError ? "❌ MiniStudio: Push ล้มเหลว" : "✅ MiniStudio: Push สำเร็จ")
                .setContentText(resultText)
                .setSmallIcon(isError ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_upload_done)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // แสดงแจ้งเตือนปกติ
                .setOngoing(false)       // ตั้งให้ปัดลบออกเองได้
                .setAutoCancel(true)     // กดแตะที่แจ้งเตือนแล้วให้ซ่อนอัตโนมัติ
                .setProgress(0, 0, false); // เอาหลอดโหลดออก

        // ใช้ NOTIFICATION_FINAL_ID เพื่อไม่ให้โดนลบตอน stopForeground
        notificationManager.notify(NOTIFICATION_FINAL_ID, finalBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GitHub Sync Progress",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("แสดงความคืบหน้าการ Push โค้ดขึ้น GitHub");
            channel.setSound(null, null);
            channel.enableVibration(false);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
