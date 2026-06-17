package com.dev.ministudio;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;

public class BuildEnvironmentManager {

    private final Context context;

    public BuildEnvironmentManager(Context context) {
        this.context = context;
    }

    // 🌟 เตรียม Workflow สำหรับใช้ระบบจัดตั้ง Gradle อัตโนมัติจาก GitHub Actions
    public void prepareGitHubWorkflow(String localProjectPath, String projectName, String packageName, String language, int minSdk) {
        try {
            File workflowDir = new File(localProjectPath, ".github/workflows");
            if (!workflowDir.exists()) {
                workflowDir.mkdirs();
            }

            File buildYamlFile = new File(workflowDir, "build.yml");

            // สคริปต์บิวด์เวอร์ชันคลีน เพิ่มประสิทธิภาพโดยใช้ --build-cache และยุบรวมขั้นตอนให้คอมไพล์เร็วขึ้น
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
          
            // ส่งค่าไปจัดการโครงสร้างไฟล์ระบบโปรเจกต์ต่อ
            setupGradleProjectFiles(localProjectPath, projectName, packageName, language, minSdk);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupGradleProjectFiles(String rootPath, String projectName, String packageName, String language, int minSdk) {
        // 1. สร้าง settings.gradle
        String settingsContent = """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = '%s'
                include ':app'
                """.formatted(projectName);
        writeFile(new File(rootPath, "settings.gradle"), settingsContent);

        // 2. สร้าง root build.gradle
        String rootBuildGradle;
        if ("Kotlin".equals(language)) {
            rootBuildGradle = """
                    plugins {
                        id 'com.android.application' version '8.4.0' apply false
                        id 'com.android.library' version '8.4.0' apply false
                        id 'org.jetbrains.kotlin.android' version '1.9.0' apply false
                    }
                    """;
        } else {
            rootBuildGradle = """
                    plugins {
                        id 'com.android.application' version '8.4.0' apply false
                        id 'com.android.library' version '8.4.0' apply false
                    }
                    """;
        }
        
        File rootBuildGradleFile = new File(rootPath, "build.gradle");
        if (!rootBuildGradleFile.exists()) {
            writeFile(rootBuildGradleFile, rootBuildGradle);
        }

        // 3. สร้าง app/build.gradle
        File appDir = new File(rootPath, "app");
        if (!appDir.exists()) appDir.mkdirs();

        StringBuilder appBuildGradle = new StringBuilder();
        appBuildGradle.append("plugins {\n");
        appBuildGradle.append("    id 'com.android.application'\n");
        if ("Kotlin".equals(language)) {
            appBuildGradle.append("    id 'org.jetbrains.kotlin.android'\n");
        }
        appBuildGradle.append("}\n\n");

        appBuildGradle.append("android {\n");
        appBuildGradle.append("    namespace '").append(packageName).append("'\n");
        appBuildGradle.append("    compileSdk 34\n\n");

        appBuildGradle.append("    defaultConfig {\n");
        appBuildGradle.append("        applicationId '").append(packageName).append("'\n");
        appBuildGradle.append("        minSdk ").append(minSdk).append("\n"); 
        appBuildGradle.append("        targetSdk 34\n");
        appBuildGradle.append("        versionCode 1\n");
        appBuildGradle.append("        versionName '1.0'\n");
        appBuildGradle.append("    }\n\n");

        appBuildGradle.append("    sourceSets {\n");
        appBuildGradle.append("        main {\n");
        appBuildGradle.append("            manifest.srcFile 'src/main/AndroidManifest.xml'\n");
        if ("Kotlin".equals(language)) {
            appBuildGradle.append("            java.srcDirs = ['src/main/kotlin']\n");
        } else {
            appBuildGradle.append("            java.srcDirs = ['src/main/java']\n");
        }
        appBuildGradle.append("            res.srcDirs = ['src/main/res']\n");
        appBuildGradle.append("        }\n");
        appBuildGradle.append("    }\n\n");

        appBuildGradle.append("    buildTypes {\n");
        appBuildGradle.append("        debug { debuggable true }\n");
        appBuildGradle.append("        release { minifyEnabled false }\n");
        appBuildGradle.append("    }\n\n");

        appBuildGradle.append("    compileOptions {\n");
        appBuildGradle.append("        sourceCompatibility JavaVersion.VERSION_17\n");
        appBuildGradle.append("        targetCompatibility JavaVersion.VERSION_17\n");
        appBuildGradle.append("    }\n");
        appBuildGradle.append("}\n\n");

        appBuildGradle.append("// แก้ปัญหา Duplicate Kotlin\n");
        appBuildGradle.append("configurations.all {\n");
        appBuildGradle.append("    resolutionStrategy {\n");
        appBuildGradle.append("        eachDependency { DependencyResolveDetails details ->\n");
        appBuildGradle.append("            if (details.requested.group == 'org.jetbrains.kotlin' && \n");
        appBuildGradle.append("                details.requested.name.startsWith('kotlin-stdlib')) {\n");
        appBuildGradle.append("                details.useVersion('1.9.0')\n");
        appBuildGradle.append("            }\n");
        appBuildGradle.append("        }\n");
        appBuildGradle.append("    }\n");
        appBuildGradle.append("}\n\n");

        appBuildGradle.append("dependencies {\n");
        appBuildGradle.append("    implementation 'androidx.appcompat:appcompat:1.6.1'\n");
        appBuildGradle.append("    implementation 'com.google.android.material:material:1.9.0'\n");
        appBuildGradle.append("    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'\n");
        appBuildGradle.append("    \n");
        appBuildGradle.append("    // JGit\n");
        appBuildGradle.append("    implementation('org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r') {\n");
        appBuildGradle.append("        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'\n");
        appBuildGradle.append("        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'\n");
        appBuildGradle.append("    }\n");
        appBuildGradle.append("    \n");
        appBuildGradle.append("    implementation 'androidx.core:core:1.10.1'\n");
        appBuildGradle.append("    \n");
        appBuildGradle.append("    // Kotlin\n");
        appBuildGradle.append("    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.0'\n");
        appBuildGradle.append("    \n");
        appBuildGradle.append("    testImplementation 'junit:junit:4.13.2'\n");
        appBuildGradle.append("    androidTestImplementation 'androidx.test.ext:junit:1.1.5'\n");
        appBuildGradle.append("    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'\n");
        appBuildGradle.append("}\n");

        File appBuildGradleFile = new File(appDir, "build.gradle");
        if (!appBuildGradleFile.exists()) {
            writeFile(appBuildGradleFile, appBuildGradle.toString());
        }

        // 4. สร้าง gradle.properties (เปิดใช้งานระบบคู่ขนานและระบบแคชความเร็วสูง)
        String gradlePropertiesContent = """
                android.useAndroidX=true
                android.enableJetifier=true
                android.nonTransitiveRClass=true
                org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
                org.gradle.caching=true
                org.gradle.parallel=true
                """;
        writeFile(new File(rootPath, "gradle.properties"), gradlePropertiesContent);

        // 5. ปรับปรุงไฟล์ .gitignore ใหม่ ไม่ต้องกันพื้นที่ให้ไฟล์ wrapper แล้ว
        String rootGitignore = """
                .gradle/
                build/
                app/build/
                captures/
                .externalNativeBuild/
                .cxx/
                local.properties
                .idea/
                """;
        writeFile(new File(rootPath, ".gitignore"), rootGitignore);
    }

    private void writeFile(File targetFile, String content) {
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(content.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
