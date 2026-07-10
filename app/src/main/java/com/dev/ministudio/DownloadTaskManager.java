package com.dev.ministudio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class DownloadTaskManager {

    public interface DownloadListener {
        void onDownloadLog(String text, int color);
        void onDownloadFinished(boolean success, File apkFile);
    }

    private final Context context;
    private final String projectName;
    private final DownloadListener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final int COLOR_INFO = Color.parseColor("#4FC3F7");
    private final int COLOR_SUCCESS = Color.parseColor("#81C784");
    private final int COLOR_ERROR = Color.parseColor("#FF8A80");
    private final int COLOR_WARNING = Color.parseColor("#FFB74D");

    private int lastBytesProgress = -1;

    public DownloadTaskManager(Context context, String projectName, DownloadListener listener) {
        this.context = context;
        this.projectName = projectName;
        this.listener = listener;
    }

    public void startFetchAndInstall() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            HttpURLConnection dlConn = null;
            try {
                SharedPreferences prefs = context.getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
                String username = prefs.getString("username", "").trim();
                String token = prefs.getString("token", "").trim();

                if (username.isEmpty() || token.isEmpty()) {
                    sendProgress("❌ กรุณาตั้งค่า GitHub Username + Token ก่อน", COLOR_ERROR);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                sendProgress("🔍 กำลังดึงข้อมูลบิวด์ล่าสุดจากคลาวด์...", COLOR_INFO);

                String releasesUrl = "https://api.github.com/repos/" + username + "/" + projectName + "/releases?per_page=5";
                
                conn = (HttpURLConnection) new URL(releasesUrl).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("Cache-Control", "no-cache, no-store");
                // ✅ แก้ไขข้อ 3: ตั้งเวลา Timeout เครือข่าย
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() != 200) {
                    sendProgress("❌ ไม่สามารถเข้าถึงข้อมูลคลาวด์บิวด์ได้ (HTTP " + conn.getResponseCode() + ")", COLOR_ERROR);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                JSONArray releases = new JSONArray(readStream(conn.getInputStream()));
                if (releases.length() == 0) {
                    sendProgress("⏳ ไม่พบประวัติการบิวด์บนระบบคลาวด์", COLOR_WARNING);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                JSONObject latestRelease = releases.getJSONObject(0);
                JSONArray assets = latestRelease.getJSONArray("assets");

                String downloadUrl = null;
                String apkName = "app-debug.apk";

                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.getString("name");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url");
                        apkName = name;
                        break;
                    }
                }

                if (downloadUrl == null) {
                    sendProgress("⏳ พบสถานะบิวด์สำเร็จ แต่ไม่พบไฟล์ APK สำหรับติดตั้ง", COLOR_WARNING);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                sendProgress("📥 ตรวจพบไฟล์: " + apkName + " (กำลังเตรียมดาวน์โหลด...)", COLOR_INFO);

                dlConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                dlConn.setRequestProperty("Authorization", "Bearer " + token);
                dlConn.setRequestProperty("Cache-Control", "no-cache");
                dlConn.setConnectTimeout(15000);
                dlConn.setReadTimeout(15000);

                // ✅ แก้ไขข้อ 2: จัดการระบบ Redirect (HTTP 302/301) ไปยัง AWS S3 อย่างปลอดภัย
                int status = dlConn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM) {
                    String redirectedUrl = dlConn.getHeaderField("Location");
                    dlConn.disconnect(); // ปิดการเชื่อมต่อเดิมที่ติด Token
                    
                    // เปิดการเชื่อมต่อตรงเข้าสู่ตู้เก็บไฟล์จริง โดยไม่แอบส่ง Token ติดไปให้โดนบล็อก
                    dlConn = (HttpURLConnection) new URL(redirectedUrl).openConnection();
                    dlConn.setConnectTimeout(15000);
                    dlConn.setReadTimeout(15000);
                }

                File outputDir = new File(context.getExternalFilesDir(null), "build_output");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                File apkFile = new File(outputDir, apkName);

                // เริ่มดาวน์โหลดข้อมูลผ่านกลไกบัฟเฟอร์ความเร็วสูง
                try (InputStream in = new BufferedInputStream(dlConn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(apkFile)) {

                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    int fileLength = Math.max(dlConn.getContentLength(), 1);

                    lastBytesProgress = -1; // รีเซ็ตตัวนับการแสดงผล

                    while ((count = in.read(data)) != -1) {
                        total += count;
                        fos.write(data, 0, count);

                        int progress = (int) (total * 100 / fileLength);
                        
                        // ✅ แก้ไขข้อ 1: ปรับวิธีนับ % ใหม่ ป้องกันการโดดข้ามเลขและทำให้แถบเปอร์เซ็นต์ลื่นไหลขึ้น
                        if (progress != lastBytesProgress && progress % 5 == 0) {
                            sendProgress("📥 กำลังดาวน์โหลดไฟล์ APK... " + progress + "%", COLOR_INFO);
                            lastBytesProgress = progress;
                        }
                    }
                }

                if (apkFile.exists() && apkFile.length() > 50000) {
                    sendProgress("🎉 ดาวน์โหลดสำเร็จเรียบร้อย! กำลังเรียกตัวติดตั้งระบบ...", COLOR_SUCCESS);
                    uiHandler.post(() -> listener.onDownloadFinished(true, apkFile));
                    installApk(apkFile);
                } else {
                    sendProgress("⚠️ ตรวจพบความเสียหาย: ไฟล์ APK มีขนาดเล็กผิดปกติ (" + apkFile.length() + " bytes)", COLOR_WARNING);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                }

            } catch (Exception e) {
                sendProgress("❌ เกิดข้อผิดพลาดขณะดาวน์โหลด: " + e.getMessage(), COLOR_ERROR);
                e.printStackTrace();
                uiHandler.post(() -> listener.onDownloadFinished(false, null));
            } finally {
                // ✅ แก้ไขข้อ 4: สั่งปิด Connection ทิ้งอย่างสมบูรณ์เพื่อเคลียร์แรมของ Network Socket
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
                if (dlConn != null) {
                    try { dlConn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private String readStream(InputStream in) throws Exception {
        // ใช้ try-with-resources เพื่อให้แน่ใจว่า Reader จะถูกปิดทันทีหลังอ่านเสร็จ
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    private void installApk(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            sendProgress("❌ ไม่สามารถเปิดหน้าต่างติดตั้งแอปพลิเคชันได้: " + e.getMessage(), COLOR_ERROR);
        }
    }

    private void sendProgress(final String text, final int color) {
        uiHandler.post(() -> listener.onDownloadLog(text, color));
    }
}
