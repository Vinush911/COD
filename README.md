## ⚙️ Setup Guide
## ⚠️ OpenCV Setup (Important)

This project uses OpenCV for image processing.
Due to issues with direct dependency resolution and large SDK size, OpenCV is integrated as a local module.

---

## 🚨 Problem Faced

* `Failed to resolve: org.opencv:opencv-android`
* `Failed to resolve: project :opencv`
* `Namespace not specified` error

---

## ✅ Solution (Step-by-Step)

### 1. Download OpenCV Android SDK

* Go to: https://opencv.org/releases/
* Download the Android SDK

---

### 2. Import OpenCV as a Module

Copy the following folder:

```
sdk/java
```

Paste it into your project root and rename it:

```
opencv/
```

---

### 3. Fix Folder Structure

Ensure structure is:

```
opencv/
├── src/
├── res/
├── AndroidManifest.xml
```

❌ Do NOT keep:

```
opencv/java/src   (wrong)
```

---

### 4. Add OpenCV Module

Edit `settings.gradle.kts`:

```kotlin
include(":opencv")
```

---

### 5. Add Dependency

In `app/build.gradle.kts`:

```kotlin
implementation(project(":opencv"))
```

---

### 6. Create OpenCV build.gradle

Inside `opencv/`, create `build.gradle`:

```gradle
apply plugin: 'com.android.library'

android {
    namespace "org.opencv"
    compileSdkVersion 34

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
    }
}
```

---

### 7. Sync Project

* Click **Sync Now** in Android Studio
* Build the project

---

## ⚠️ Notes

* Do NOT push full OpenCV SDK to GitHub (large size)
* Only include required module files
* Avoid using Maven dependency (not reliable)

---

## ✅ Result

* OpenCV works correctly
* No dependency resolution errors
* Clean and manageable project structure
