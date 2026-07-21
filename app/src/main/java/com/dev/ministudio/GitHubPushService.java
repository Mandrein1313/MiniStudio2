package com.dev.ministudio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.io.File;

public class GitHubPushService extends Service {
    private static final String CHANNEL_ID = "github_push_channel";
    private static final int NOTIFICATION_ID = 1001;
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

        // 1. เริ่มรันแบบ Foreground Service ทันทีเพื่อให้ระบบรู้ว่าห้ามสั่งปิดงานนี้
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚀 MiniStudio GitHub Push")
                .setContentText("กำลังเตรียมระบบส่งโค้ด...")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // ห้ามปัดหมุดแจ้งเตือนทิ้ง
                .setProgress(100, 0, true); // แถบวิ่งแบบไม่ระบุเวลา (Indeterminate)

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        // 2. แตกเธรดทำงาน Git ด้านหลัง
        new Thread(() -> {
            File projectDir = new File("/sdcard/MiniStudio/" + projectName);
            try {
                Git git;
                
                // [1/4] ตรวจสอบระบบ Git
                updateNotification("[1/4] ตรวจสอบสถานะ Git...");
                if (!new File(projectDir, ".git").exists()) {
                    git = Git.init().setDirectory(projectDir).call();
                } else {
                    git = Git.open(projectDir);
                }

                // [2/4] Add
                updateNotification("[2/4] กำลังรวบรวมไฟล์ทั้งหมด...");
                git.add().addFilepattern(".").call();

                // [3/4] Commit
                updateNotification("[3/4] บันทึกประวัติประมวลผล (Commit)...");
                try {
                    git.commit().setMessage("Updated via MiniStudio - " + new java.util.Date()).call();
                } catch (Exception ce) {
                    // ไม่มีข้อมูลแก้ไข ข้ามได้
                }

                // ผูกรีโมท
                git.getRepository().getConfig().setString("remote", "origin", "url", repoUrl);
                git.getRepository().getConfig().save();

                // [4/4] Push
                updateNotification("[4/4] กำลังอัปโหลดข้อมูลไป GitHub... (ปิดหน้าจอรอได้เลยครับน้า)");
                git.push()
                   .setRemote(repoUrl)
                   .setPushAll()
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                   .call();

                // 🌟 ทำงานสำเร็จ! เปลี่ยนแจ้งเตือนเป็นแบบกดปิดได้
                showFinalNotification("🎉 อัปโหลดโค้ดขึ้น GitHub สำเร็จแล้วครับน้า!", false);

            } catch (Exception e) {
                e.printStackTrace();
                showFinalNotification("❌ Push ล้มเหลว: " + e.getMessage(), true);
            } finally {
                // หยุดทำงาน Service เมื่อเสร็จสิ้นกระบวนการทั้งหมด
                stopForeground(false);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void updateNotification(String statusText) {
        notificationBuilder.setContentText(statusText);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void showFinalNotification(String resultText, boolean isError) {
        NotificationCompat.Builder finalBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(isError ? "❌ MiniStudio Error" : "🎉 MiniStudio Success")
                .setContentText(resultText)
                .setSmallIcon(isError ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_upload_done)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(false) // ให้ผู้ใช้ปัดทิ้งได้แล้ว
                .setProgress(0, 0, false); // ลบแถบดาวน์โหลดออก

        notificationManager.notify(NOTIFICATION_ID, finalBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GitHub Sync Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("ใช้สำหรับแสดงความคืบหน้าการส่งโค้ดขึ้นคลัง GitHub");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
