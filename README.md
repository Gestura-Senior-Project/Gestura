Gestura (Unit 7)

## Table of Contents

1. [Overview](#Overview)
2. [Technical Architecture](#Technical-Architecture)
   - [Database Schema](#Database-Schema)
   - [Core Fragments](#Core-Fragments)
3. [Product Spec](#Product-Spec)
4. [User Manual](#User-Manual)
5. [Wireframes](#Wireframes)

---

## Overview

### Description

**Gestura** is an AI-powered mobile app. It translates American Sign Language (ASL) gestures into text and speech — and vice versa — in real time. The app also allows users to learn ASL, contribute gesture data to train models, and stay updated as it with the latest AI model.  
Gestura bridges the communication gap between Deaf and hearing communities through accessibility-focused innovation.

---

## Technical Architecture

### Database Schema

Gestura uses **Firebase Firestore** as its primary NoSQL database. The schema is organized into three main collections to manage the "Contribute → Review → Accept" pipeline:

#### 1. `asl_review` (Pending Contributions)
Stores gestures uploaded by users that are waiting for developer audit.
- `word`: String (The intended sign)
- `predictedLabel`: String (The label predicted by the on-device model)
- `confidence`: Double (Confidence score 0-100)
- `videoUrl`: String (Firebase Storage link to the MP4 file)
- `userEmail`: String (Contributor identity)
- `keypoints`: List<Double> (Flattened MediaPipe landmark data)
- `createdAt`: Timestamp
- `isMismatch`: Boolean (True if typed word != predicted label)

#### 2. `asl_accepted` (Verified Data)
Verified contributions moved from `asl_review`. These are used for periodic model retraining.
- (Inherits fields from `asl_review`)
- `status`: "accepted"

#### 3. `asl_reference` (Learning Material)
Reference videos shown to users in the Contribute tab.
- `displayWord`: String
- `storagePath`: String (Path to reference video in Cloud Storage)

---

### Core Fragments & Logic

#### 1. ASL Translation (`OnDeviceCaptionFragment.kt`)
Handles live camera processing and video classification.
- **Inference:** Uses `AslSamplePipeline` to extract landmarks via a remote Holistic server and classifies them locally using a TFLite LSTM model.
- **Key Logic:**
```kotlin
// Example of triggering on-device classification from a video URI
private fun classifyVideo(uri: Uri) {
    lifecycleScope.launch(Dispatchers.IO) {
        val pipeline = AslSamplePipeline(requireContext())
        val result = pipeline.run(word = "unknown", videoUri = uri)
        withContext(Dispatchers.Main) {
            currentCaption = Caption(text = result.predictedLabel, confidence = result.confidence)
            updateUI()
        }
    }
}
```

#### 2. Contributions (`ContributeFragment.kt`)
Allows users to record and upload signs.
- **Validation:** Compares user-typed labels against model predictions before submission.
- **Data Pipeline:** Uploads raw video to Firebase Storage and metadata to Firestore.

#### 3. Avatar Rendering (`AvatarService.kt`)
Communicates with the GenASL API to generate 3D sign animations from text.
- **Modern Networking:** Uses OkHttp extension functions for clean MediaType handling.
```kotlin
fun generateAvatar(text: String, callback: AvatarCallback) {
    val request = Request.Builder()
        .url("$baseUrl/generate-asl")
        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
        .header("x-api-key", BuildConfig.GENASL_API_KEY)
        .build()
    client.newCall(request).enqueue(...)
}
```

---

## Product Spec

### 1. User Features (Required and Optional)

**Required Features**
1. Camera-based ASL gesture recognition → displays translated text  
2. Text-to-speech conversion for recognized signs  
4. User authentication (Firebase login/sign-up)  
5. Model update button to sync with the latest AI model  
5. Animated ASL avatar for reverse translation (speech/text → sign)  
6. Gesture training feature allowing users to upload new ASL images to the cloud  
7. Multilingual translation support (Spanish, Japanese, etc.)  
8. Offline mode with cached AI models  
9. Dark Mode
10. See number of valid contributions and accuracy

---

## User Manual

Detailed instructions on how to use the app can be found in the [User Manual (TXT)](UserManual.txt).

### Core Features at a Glance:
*   **ASL Translation:** Live camera detection or video upload.
*   **ASL Avatar:** Convert text into sign language animations.
*   **Contribute:** Help train the AI by uploading your own gestures.
*   **Settings:** Customize themes and sync model updates.

---

## Wireframes
![Justora Wireframes](docs/justora-wireframe.svg)

<br>

# Milestone 2 - Build Sprint 1 (Unit 8)

## GitHub Project board
![Milestone Board](allmile.png)

## Issues worked on this sprint
**Completed (3/3):**
- ✅ **Bottom Navigation** — Implemented Material BottomNavigationView wired to Navigation Component.
- ✅ **Login** — Email/password auth with Firebase.
- ✅ **Language translation tab** — Compose-based screen embedded in Fragment.

<br>

# Milestone 3 - Build Sprint 2 (Unit 9)

## Completed user stories
- ✅ **ASL Tab** — Camera preview, capture pipeline, Holistic → LSTM inference  
- ✅ **Contribute Tab** — Upload gesture samples, store metadata, validation  
- ✅ **Data Backend** — Firestore + Storage + Functions for contributions & model training  
- ✅ **UI Polish (V1)** — Cleaner typography, spacing, accessibility labels

## Build progress (GIFs) 

**ASL Translation Flow (Camera → Landmarks → Text)**
![ASL Translation](3.gif)

**Contribution Flow (Upload → Validate → Submit)**
![Contribute](4.gif)

## App Demo Video
▶️ [Watch the full demo](https://www.canva.com/design/DAG5rJy2Dl8/kSQlA8HvxSn-BRn41vgXGg/watch?utm_content=DAG5rJy2Dl8&utm_campaign=designshare&utm_medium=link2&utm_source=uniquelinks&utlId=hd5119630c7)
