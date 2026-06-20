package com.dev.ministudio;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

public class BuildEnvironmentManager {

    private final Context context;

    public BuildEnvironmentManager(Context context) {
        this.context = context;
    }

    // 🌟 ระบบตรวจสอบ: ถ้าโปรเจกต์ใช้ .kts อยู่แล้ว ให้ข้ามการสร้างไฟล์ทับ
    private boolean isModernGradleProject(String rootPath) {
        File rootDir = new File(rootPath);
        if (!rootDir.exists()) return false;
        
        // ถ้าเจอไฟล์ .kts ให้ถือว่าเป็นโปรเจกต์สมัยใหม่ ไม่ต้องยุ่งกับมัน
        return new File(rootPath, "build.gradle.kts").exists() || 
               new File(rootPath, "settings.gradle.kts").exists();
    }

    // 🌟 เตรียม Workflow สำหรับ GitHub Actions (เฉพาะโปรเจกต์ที่ยังไม่มีโครงสร้าง)
    public void prepareGitHubWorkflow(String localProjectPath, String projectName, String packageName, String language, int minSdk) {
        // ถ้าเป็นโปรเจกต์ .kts ให้รีเทิร์นออกทันที ไม่ทำอะไรทั้งสิ้น
        if (isModernGradleProject(localProjectPath)) return;

        try {
            File workflowDir = new File(localProjectPath, ".github/workflows");
            if (!workflowDir.exists()) {
                workflowDir.mkdirs();
            }

            File buildYamlFile = new File(workflowDir, "build.yml");
            if (!buildYamlFile.exists()) {
                String workflowContent = "name: Android Cloud Build Pipeline\n\n" +
                        "on:\n" +
                        "  push:\n" +
                        "    branches: [ \"main\", \"master\" ]\n" +
                        "  workflow_dispatch:\n\n" +
                        "permissions:\n" +
                        "  contents: write\n\n" +
                        "jobs:\n" +
                        "  build:\n" +
                        "    runs-on: ubuntu-latest\n\n" +
                        "    steps:\n" +
                        "    - name: Checkout repository\n" +
                        "      uses: actions/checkout@v4\n\n" +
                        "    - name: Set up JDK 17\n" +
                        "      uses: actions/setup-java@v4\n" +
                        "      with:\n" +
                        "        distribution: 'temurin'\n" +
                        "        java-version: '17'\n\n" +
                        "    - name: Setup Gradle 8.13\n" +
                        "      uses: gradle/actions/setup-gradle@v4\n" +
                        "      with:\n" +
                        "        gradle-version: '8.13'\n\n" +
                        "    - name: Clean & Build Debug APK\n" +
                        "      run: gradle clean assembleDebug --no-daemon --build-cache\n\n" +
                        "    - name: Generate Timestamp\n" +
                        "      id: timestamp\n" +
                        "      run: echo \"timestamp=$(date +'%Y%m%d-%H%M%S')\" >> $GITHUB_OUTPUT\n\n" +
                        "    - name: Upload APK to Release\n" +
                        "      uses: softprops/action-gh-release@v2\n" +
                        "      with:\n" +
                        "        tag_name: \"build-${{ steps.timestamp.outputs.timestamp }}\"\n" +
                        "        name: \"Build ${{ steps.timestamp.outputs.timestamp }}\"\n" +
                        "        draft: false\n" +
                        "        prerelease: false\n" +
                        "        make_latest: true\n" +
                        "        overwrite_files: true\n" +
                        "        files: app/build/outputs/apk/debug/*.apk\n" +
                        "      env:\n" +
                        "        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}\n";

                writeFile(buildYamlFile, workflowContent);
            }
          
            setupGradleProjectFiles(localProjectPath, projectName, packageName, language, minSdk);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
public void importV2rayNGProject(String localProjectPath) {
    File projectDir = new File(localProjectPath);
    if (!projectDir.exists()) {
        projectDir.mkdirs();
    }

    try {
        boolean isNewProject = false;

        // 1. settings.gradle.kts
        File settingsFile = new File(projectDir, "settings.gradle.kts");
        if (!settingsFile.exists()) {
            String settingsContent = "pluginManagement {\n" +
                    "    repositories {\n" +
                    "        google()\n" +
                    "        mavenCentral()\n" +
                    "        gradlePluginPortal()\n" +
                    "    }\n" +
                    "}\n\n" +
                    "dependencyResolutionManagement {\n" +
                    "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n" +
                    "    repositories {\n" +
                    "        google()\n" +
                    "        mavenCentral()\n" +
                    "        maven { url = uri(\"https://jitpack.io\") }\n" +
                    "    }\n" +
                    "}\n\n" +
                    "rootProject.name = \"v2rayNG\"\n" +
                    "include(\":app\")\n";
            writeFile(settingsFile, settingsContent);
            isNewProject = true;
        }

        // 2. libs.versions.toml
        File gradleDir = new File(projectDir, "gradle");
        if (!gradleDir.exists()) gradleDir.mkdirs();
        
        File tomlFile = new File(gradleDir, "libs.versions.toml");
        if (!tomlFile.exists()) {
            String tomlContent = "[versions]\n" +
                    "agp = \"8.5.0\"\n" +
                    "kotlin = \"2.0.0\"\n" +
                    "coreKtx = \"1.13.1\"\n" +
                    "appcompat = \"1.7.0\"\n" +
                    "material = \"1.12.0\"\n" +
                    "gson = \"2.11.0\"\n" +
                    "okhttp = \"4.12.0\"\n" +
                    "swiperefresh = \"1.1.0\"\n" +
                    "quickie = \"1.5.0\"\n" +
                    "toasty = \"1.5.2\"\n" +
                    "mmkv = \"1.3.0\"\n" +
                    "coroutines = \"1.8.1\"\n" +
                    "multidex = \"2.0.1\"\n\n" +
                    "[libraries]\n" +
                    "androidx-core-ktx = { module = \"androidx.core:core-ktx\", version.ref = \"coreKtx\" }\n" +
                    "androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }\n" +
                    "material = { module = \"com.google.android.material:material\", version.ref = \"material\" }\n" +
                    "gson = { module = \"com.google.code.gson:gson\", version.ref = \"gson\" }\n" +
                    "okhttp = { module = \"com.squareup.okhttp3:okhttp\", version.ref = \"okhttp\" }\n" +
                    "androidx-swiperefreshlayout = { module = \"androidx.swiperefreshlayout:swiperefreshlayout\", version.ref = \"swiperefresh\" }\n" +
                    "quickie-foss = { module = \"io.github.g00fy2.quickie:quickie-foss\", version.ref = \"quickie\" }\n" +
                    "toasty = { module = \"com.github.GrenderG:Toasty\", version.ref = \"toasty\" }\n" +
                    "mmkv-static = { module = \"com.tencent.mmkv:MMKV-static\", version.ref = \"mmkv\" }\n" +
                    "kotlinx-coroutines-android = { module = \"org.jetbrains.kotlinx:kotlinx-coroutines-android\", version.ref = \"coroutines\" }\n" +
                    "multidex = { module = \"androidx.multidex:multidex\", version.ref = \"multidex\" }\n\n" +
                    "[plugins]\n" +
                    "android-application = { id = \"com.android.application\", version.ref = \"agp\" }\n" +
                    "kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }\n";
            writeFile(tomlFile, tomlContent);
            isNewProject = true;
        }

        // 3. app/build.gradle.kts
        File appDir = new File(projectDir, "app");
        if (!appDir.exists()) appDir.mkdirs();
        
        File appBuildFile = new File(appDir, "build.gradle.kts");
        if (!appBuildFile.exists()) {
            String appBuildContent = "plugins {\n" +
                    "    alias(libs.plugins.android.application)\n" +
                    "    alias(libs.plugins.kotlin.android)\n" +
                    "}\n\n" +
                    "android {\n" +
                    "    namespace = \"com.v2ray.ang\"\n" +
                    "    compileSdk = 34\n\n" +
                    "    defaultConfig {\n" +
                    "        applicationId = \"com.v2ray.ang\"\n" +
                    "        minSdk = 24\n" +
                    "        targetSdk = 34\n" +
                    "        versionCode = 1\n" +
                    "        versionName = \"1.0\"\n" +
                    "        multiDexEnabled = true\n" +
                    "    }\n\n" +
                    "    buildTypes {\n" +
                    "        release {\n" +
                    "            isMinifyEnabled = false\n" +
                    "        }\n" +
                    "    }\n\n" +
                    "    compileOptions {\n" +
                    "        isCoreLibraryDesugaringEnabled = true\n" +
                    "        sourceCompatibility = JavaVersion.VERSION_17\n" +
                    "        targetCompatibility = JavaVersion.VERSION_17\n" +
                    "    }\n\n" +
                    "    kotlin {\n" +
                    "        compilerOptions {\n" +
                    "            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)\n" +
                    "        }\n" +
                    "    }\n\n" +
                    "    buildFeatures {\n" +
                    "        viewBinding = true\n" +
                    "        buildConfig = true\n" +
                    "    }\n" +
                    "}\n\n" +
                    "dependencies {\n" +
                    "    implementation(fileTree(mapOf(\"dir\" to \"libs\", \"include\" to listOf(\"*.jar\", \"*.aar\"))))\n\n" +
                    "    implementation(libs.androidx.core.ktx)\n" +
                    "    implementation(libs.androidx.appcompat)\n" +
                    "    implementation(libs.material)\n" +
                    "    implementation(libs.gson)\n" +
                    "    implementation(libs.okhttp)\n" +
                    "    implementation(libs.androidx.swiperefreshlayout)\n" +
                    "    implementation(libs.quickie.foss)\n" +
                    "    implementation(libs.toasty)\n" +
                    "    implementation(libs.mmkv.static)\n" +
                    "    implementation(libs.kotlinx.coroutines.android)\n" +
                    "    implementation(libs.multidex)\n" +
                    "}\n";
            writeFile(appBuildFile, appBuildContent);
            isNewProject = true;
        }

        // 4. gradle.properties
        File propsFile = new File(projectDir, "gradle.properties");
        if (!propsFile.exists()) {
            String props = "android.useAndroidX=true\n" +
                          "android.enableJetifier=true\n" +
                          "org.gradle.jvmargs=-Xmx4096m\n" +
                          "org.gradle.parallel=true";
            writeFile(propsFile, props);
        }

        // Toast แบบปลอดภัย (ไม่ cast Activity)
        if (context != null) {
            String message = isNewProject ? 
                "✅ ตั้งค่า v2rayNG เรียบร้อย!" : 
                "✅ v2rayNG ตรวจสอบแล้ว (ไฟล์เดิมมีอยู่แล้ว)";
            
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }

    } catch (Exception e) {
        e.printStackTrace();
        if (context != null) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> Toast.makeText(context, "❌ เกิดข้อผิดพลาด: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }
}
// สำหรับโปรเจกต์ทั่วไป - ตรวจจับภาษา Kotlin/Java อัตโนมัติ
    public void importExistingProject(String localProjectPath) {
        File projectDir = new File(localProjectPath);
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }

        try {
            // ตรวจสอบว่าเป็นโปรเจกต์ Gradle สมัยใหม่หรือไม่
            if (isModernGradleProject(localProjectPath)) {
                showToast("✅ เป็นโปรเจกต์ Gradle สมัยใหม่ (Kotlin DSL) อยู่แล้ว");
                return;
            }

            // ตรวจจับภาษาอัตโนมัติ
            String detectedLanguage = detectProjectLanguage(localProjectPath);
            
            // ตั้งค่าโครงสร้างพื้นฐาน
            setupGradleProjectFiles(localProjectPath, 
                                  "MyApp", 
                                  "com.example.myapp", 
                                  detectedLanguage, 
                                  24);

            showToast("✅ ตั้งค่าโปรเจกต์ " + detectedLanguage + " เรียบร้อยแล้ว");

        } catch (Exception e) {
            e.printStackTrace();
            showToast("❌ Error: " + e.getMessage());
        }
    }
// เมธอดช่วยตรวจจับภาษา Kotlin หรือ Java
    private String detectProjectLanguage(String rootPath) {
        File srcMain = new File(rootPath, "app/src/main");
        
        if (!srcMain.exists()) {
            srcMain = new File(rootPath, "src/main");
        }

        if (srcMain.exists()) {
            File kotlinDir = new File(srcMain, "kotlin");
            if (kotlinDir.exists() && hasFilesWithExtension(kotlinDir, ".kt")) {   // แก้ตรงนี้
                return "Kotlin";
            }

            File javaDir = new File(srcMain, "java");
            if (javaDir.exists() && hasFilesWithExtension(javaDir, ".java")) {    // แก้ตรงนี้
                return "Java";
            }
        }

        // ตรวจหาไฟล์ .kt หรือ .java ในโปรเจกต์ทั้งหมด
        if (hasFilesWithExtension(new File(rootPath), ".kt")) {                  // แก้ตรงนี้
            return "Kotlin";
        }
        if (hasFilesWithExtension(new File(rootPath), ".java")) {                // แก้ตรงนี้
            return "Java";
        }

        return "Kotlin";
    }

    // ตรวจหาไฟล์ที่มีนามสกุลที่ระบุ
    private boolean hasFilesWithExtension(File dir, String extension) {
        if (!dir.exists() || !dir.isDirectory()) return false;
        
        File[] files = dir.listFiles();
        if (files == null) return false;
        
        for (File file : files) {
            if (file.isDirectory()) {
                if (hasFilesWithExtension(file, extension)) {
                    return true;
                }
            } else if (file.getName().toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    // เมธอดช่วยแสดง Toast แบบปลอดภัย
    private void showToast(String message) {
        if (context != null) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }
    }
    private void setupGradleProjectFiles(String rootPath, String projectName, String packageName, String language, int minSdk) {
        // กันเหนียวอีกชั้น
        if (isModernGradleProject(rootPath)) return;

        // 1. สร้าง settings.gradle
        File settingsFile = new File(rootPath, "settings.gradle");
        if (!settingsFile.exists()) {
            String settingsContent = "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n" +
                    "dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.PREFER_PROJECT); repositories { google(); mavenCentral() } }\n" +
                    "rootProject.name = '" + projectName + "'\ninclude ':app'\n";
            writeFile(settingsFile, settingsContent);
        }

        // 2. สร้าง root build.gradle
        File rootBuildGradleFile = new File(rootPath, "build.gradle");
        if (!rootBuildGradleFile.exists()) {
            String rootBuildGradle = "Kotlin".equals(language) ? 
                    "plugins { id 'com.android.application' version '8.4.0' apply false; id 'com.android.library' version '8.4.0' apply false; id 'org.jetbrains.kotlin.android' version '1.9.0' apply false }" :
                    "plugins { id 'com.android.application' version '8.4.0' apply false; id 'com.android.library' version '8.4.0' apply false }";
            writeFile(rootBuildGradleFile, rootBuildGradle);
        }

        // 3. สร้าง app/build.gradle
        File appDir = new File(rootPath, "app");
        if (!appDir.exists()) appDir.mkdirs();
        File appBuildGradleFile = new File(appDir, "build.gradle");
        
        if (!appBuildGradleFile.exists()) {
            String appBuildGradle = "plugins {\n    id 'com.android.application'\n" + 
                    ("Kotlin".equals(language) ? "    id 'org.jetbrains.kotlin.android'\n" : "") + 
                    "}\n\nandroid {\n    namespace '" + packageName + "'\n    compileSdk 34\n" +
                    "    defaultConfig {\n        applicationId '" + packageName + "'\n        minSdk " + minSdk + "\n        targetSdk 34\n    }\n" +
                    "    compileOptions {\n        sourceCompatibility JavaVersion.VERSION_17\n        targetCompatibility JavaVersion.VERSION_17\n    }\n}\n" +
                    "dependencies {\n    implementation 'androidx.appcompat:appcompat:1.6.1'\n    implementation 'com.google.android.material:material:1.9.0'\n}";
            writeFile(appBuildGradleFile, appBuildGradle);
        }

        // 4. สร้าง gradle.properties
        File gradleProps = new File(rootPath, "gradle.properties");
        if (!gradleProps.exists()) {
            String content = "android.useAndroidX=true\nandroid.enableJetifier=true\norg.gradle.caching=true\norg.gradle.parallel=true";
            writeFile(gradleProps, content);
        }

        // 5. สร้าง .gitignore
        File gitIgnore = new File(rootPath, ".gitignore");
        if (!gitIgnore.exists()) {
            writeFile(gitIgnore, ".gradle/\nbuild/\napp/build/\nlocal.properties\n.idea/");
        }
    }

    private void writeFile(File targetFile, String content) {
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(content.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
