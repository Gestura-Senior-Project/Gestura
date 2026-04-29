# Gestura: An AI-Powered Mobile Application for Inclusive Sign Language Communication

**Author:** Raven Mott  
**Department:** Computer Science, Virginia State University  
**Email:** Rmot1202@students.vsu.edu  
**Supervisor:** [Supervisor Name]  
**Date:** May 2024

---

## Table of Contents

1. [Overview](#overview)
   - [Abstract](#abstract)
   - [Motivation](#motivation)
2. [Introduction & Related Work](#introduction--related-work)
3. [Problem Definition](#problem-definition)
   - [Goals & Deliverables](#goals--deliverables)
   - [Specifications & Constraints](#specifications--constraints)
4. [Technical Architecture](#technical-architecture)
   - [System Components](#system-components)
   - [Methodology](#methodology)
   - [Database Schema](#database-schema)
5. [Concepts Considered & Selection](#concepts-considered--selection)
6. [Design & Implementation](#design--implementation)
   - [Core Fragments](#core-fragments)
   - [Code Snippets](#code-snippets)
7. [Impact & Considerations](#impact--considerations)
8. [User Manual](#user-manual)
9. [Milestones & Progress](#milestones--progress)

---

## Overview

### Abstract
American Sign Language (ASL) serves as a primary means of communication for many Deaf and hard-of-hearing individuals, yet significant barriers persist when interacting with non-signers. Gestura is an AI-powered mobile application designed to make ASL translation more intuitive, portable, and inclusive. The system integrates real-time ASL recognition, natural-language sentence generation, and an ASL Video Dictionary. By combining on-device temporal gesture models with cloud-backed data pipelines, Gestura addresses latency and privacy concerns while maintaining a scalable platform for community-driven dataset growth.

### Description
**Gestura** translates ASL gestures into text and speech — and vice versa — in real time. It provides a unified architecture that integrates temporal gesture recognition, natural-language generation, and an ASL Dictionary visualization system.

### Motivation
The project is inspired by first-hand experiences with communication barriers. Growing up with an aunt who is hard of hearing, I witnessed the challenges my family faced in finding intuitive, modern ASL tools. Gestura aims to bridge this gap through a mobile-first, privacy-preserving platform.

---

## Introduction & Related Work

American Sign Language (ASL) is used by millions, yet communication gaps often require human interpreters or slow text exchanges. Gestura addresses these gaps by using on-device processing and NLP.

### Related Work
- **Bantupalli and Xie (2018):** Explores ASL recognition using deep learning, highlighting how temporal models (RNNs/LSTMs) improve accuracy over static classifiers by capturing motion dynamics.
- **Karthikeyan (2018):** Discusses mobile machine learning strategies, emphasizing on-device inference to achieve real-time performance while preserving privacy.
- **Roh et al. (2021):** Analyzes data collection for ML, advocating for diverse and validated datasets, which supports Gestura's community contribution model.
- **Miljkovic et al. (2024):** Validates the use of Firebase as a scalable backend for multi-user Android applications.

---

## Problem Definition

Despite progress in AI, there is no comprehensive mobile platform that recognizes dynamic, continuous ASL, produces fluent sentences, and supports community-driven data expansion.

### Goals & Deliverables
- **Functional Prototype:** Android app with real-time recognition.
- **ML Models:** Trained TFLite models for gesture classification.
- **Data Pipeline:** Firebase integration for contributions and updates.
- **Dictionary System:** ASL video lookup for learning and verification.

### Specifications & Constraints
- **Enabled Capabilities:** On-device TFLite inference, cloud-synced model updates, and multilingual support.
- **Constraints:** Mobile hardware limits model complexity; recognition accuracy is sensitive to lighting and background variability; initial vocabulary is limited to a core subset of ASL.

---

## Technical Architecture

### System Components

#### Device-Side (Edge)
- **CameraX Capture:** Real-time frame streaming.
- **MediaPipe Feature Extraction:** Extracts landmarks (hands/body) to reduce data dimensionality.
- **Temporal Classifier:** TensorFlow Lite models (LSTM/GRU) mapping sequences to ASL gloss tokens.
- **TTS:** Android's TextToSpeech API for vocalization.

#### Cloud-Side (Firebase)
- **Authentication:** Role-gated access (User, Reviewer, Developer).
- **Firestore:** Real-time metadata for contributions and model manifests.
- **Storage:** Media samples and model distribution.

### Methodology
Gestura follows a hybrid edge-cloud design. Inference happens on-device to minimize latency and protect privacy. The cloud is used for heavy-lifting tasks like dataset curation, reviewer auditing, and delivering model updates.

### Database Schema (Firestore)

#### 1. `users/{uid}`
- `email`: String
- `role`: String (user, reviewer, developer)
- `stats`: Map (total_contributions, accepted_count, accuracy)

#### 2. `asl_review` (Triage Pipeline)
- `word`: String (intended sign)
- `predictedLabel`: String (model output)
- `confidence`: Double (0-100)
- `videoUrl`: String (Firebase Storage link)
- `keypoints`: List<Double> (MediaPipe landmarks)
- `status`: String (pending, approved, rejected)
- `isMismatch`: Boolean (typed word != predicted)

#### 3. `asl_accepted` (Verified Dataset)
- Contains verified samples moved from `asl_review` after developer approval.

#### 4. `asl_reference`
- `displayWord`: String
- `storagePath`: String (Path to reference video in Firebase Storage)

---

## Concepts Considered & Selection

### 1. Integrated Development Environment (IDE)
- **Options:** Android Studio vs. Visual Studio Code.
- **Selection:** **Android Studio**. While VS Code is lighter, Android Studio provides the official emulator, native Kotlin support, and direct TFLite integration required for this project.

### 2. Backend Infrastructure
- **Options:** Firebase vs. Custom FastAPI/Node.js Server.
- **Selection:** **Firebase**. BaaS (Backend-as-a-Service) allowed for rapid development of Auth and Storage without the overhead of maintaining custom server infrastructure.

### 3. Feature Extraction
- **Options:** Raw RGB Video vs. MediaPipe Landmarks.
- **Selection:** **MediaPipe Landmarks**. Extracting keypoints on-device significantly reduces the input size for the LSTM model and improves privacy by not transmitting raw video for processing.

---

## Design & Implementation

### Core Fragments
- **`OnDeviceCaptionFragment`:** The core ASL translation screen. Orchestrates CameraX, MediaPipe, and TFLite inference.
- **`ContributeFragment`:** A guided UI for users to submit samples, including validation against the current model.
- **`AvatarFragment`:** Now functions as an **ASL Video Dictionary**, allowing users to search and play reference clips directly from Firebase Storage.
- **`DevReviewFragment`:** A role-gated interface for developers to approve/reject contributions via swipe gestures.

### Code Snippets

**On-Device Classification Trigger:**
```kotlin
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

**Dictionary Video Lookup:**
```kotlin
private fun lookupWord(word: String) {
    lifecycleScope.launch {
        val doc = db.collection("asl_reference").document(word.lowercase()).get().await()
        if (doc.exists()) {
            val storagePath = doc.getString("storagePath")
            // Play video from Firebase Storage...
        }
    }
}
```

---

## Impact & Considerations

### Impact Analysis
- **Local/Individual:** Improved independence and accessibility for Deaf individuals in daily social interactions.
- **Organizational:** Enhanced inclusivity in healthcare and educational environments.
- **Global:** Potential for cross-cultural communication by expanding to support multiple sign languages.

### Ethical & Legal
- **Data Privacy:** Prioritizing on-device processing.
- **Bias Mitigation:** Actively diversifying user-contributed datasets.
- **Security:** Secure model distribution via cryptographic manifest signing.

---

## User Manual

Detailed instructions can be found in the [User Manual (TXT)](UserManual.txt) or the formal [Project Documentation (MD)](ProjectDocumentation.md).

---

## Milestones & Progress

### Milestone 2 - Build Sprint 1
- ✅ **Bottom Navigation:** Material Component integration.
- ✅ **Firebase Auth:** Email/password login & mode-swapping UI.
- ✅ **Translate Tab:** Compose-based multilingual text translation.

### Milestone 3 - Build Sprint 2
- ✅ **ASL Tab:** Live CameraX + MediaPipe + TFLite inference.
- ✅ **Contribute Tab:** Guided capture and Firestore/Storage pipeline.
- ✅ **UI Polish:** Consistent Material3 theming and dark mode support.
- ✅ **Dictionary Tab:** Integrated Firebase Storage video lookup (replaces Avatar API).

## App Demo Video
▶️ [Watch the full demo](https://www.canva.com/design/DAG5rJy2Dl8/kSQlA8HvxSn-BRn41vgXGg/watch?utm_content=DAG5rJy2Dl8&utm_campaign=designshare&utm_medium=link2&utm_source=uniquelinks&utlId=hd5119630c7)
