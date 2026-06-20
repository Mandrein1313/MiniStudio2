package com.dev.ministudio;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;

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
        // สำหรับ v2rayNG เราจะไม่ overwrite ทุกอย่าง แต่จะตรวจและเพิ่มเฉพาะที่ขาด
        File settingsFile = new File(projectDir, "settings.gradle.kts");
        if (!settingsFile.exists()) {
            // สร้าง settings ใหม่ (ถ้ายังไม่มี)
            String settingsContent = """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                        maven { url = uri("https://jitpack.io") }
                    }
                }
                rootProject.name = "v2rayNG"
                include(":app")
                """;
            writeFile(settingsFile, settingsContent);
        }

        // ตรวจสอบ libs.versions.toml
        File gradleDir = new File(projectDir, "gradle");
        if (!gradleDir.exists()) gradleDir.mkdirs();
        
        File tomlFile = new File(gradleDir, "libs.versions.toml");
        if (!tomlFile.exists()) {
            // ใช้เวอร์ชันย่อที่เราสร้าง
            String tomlContent = """ /* ใส่เนื้อหา toml เดิมที่คุณมี */ """;
            writeFile(tomlFile, tomlContent);
        }

        // สำคัญ: ไม่ overwrite app/build.gradle.kts ถ้ามีอยู่แล้ว
        File appBuildFile = new File(projectDir, "app/build.gradle.kts");
        if (!appBuildFile.exists()) {
            // สร้างเฉพาะกรณีไม่มี
            String appBuildContent = """ /* เนื้อหา build.gradle.kts เดิม */ """;
            writeFile(appBuildFile, appBuildContent);
        }

        Toast.makeText(context, "✅ ตั้งค่า v2rayNG เรียบร้อย (ไม่ทับไฟล์เดิม)", Toast.LENGTH_LONG).show();

    } catch (Exception e) {
        e.printStackTrace();
        Toast.makeText(context, "เกิดข้อผิดพลาด: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
