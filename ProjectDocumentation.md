# Project Documentation: Gestura

**Author:** Raven Mott  
**Supervisor:** [Supervisor Name]  
**Date:** May 2024

---

## 1. Abstract
American Sign Language (ASL) serves as a primary means of communication for many Deaf and hard-of-hearing individuals, yet significant barriers persist when interacting with non-signers. Gestura is an AI-powered mobile application designed to make ASL translation more intuitive, portable, and inclusive. The system integrates real-time ASL recognition, natural-language sentence generation, and animated text-to-ASL avatar output. By combining on-device temporal gesture models with cloud-backed data pipelines for validation and model updates, Gestura addresses latency and privacy concerns while maintaining a scalable platform for community-driven dataset growth.

## 2. Table of Contents
1. [Introduction & Related Work](#4-introduction--related-work)
2. [Problem Definition](#5-problem-definition)
3. [Methodology](#6-methodology)
4. [Concepts Considered](#7-concepts-considered)
5. [Concept Selection](#8-concept-selection)
6. [Design and Implementation](#9-design-and-implementation)
7. [Conclusion/Summary](#10-conclusionsummary)
8. [Appendices](#12-appendices)

---

## 4. Introduction & Related Work
American Sign Language (ASL) is used by millions, yet communication gaps often require human interpreters or slow text exchanges. Gestura is motivated by first-hand experiences with accessibility challenges and aims to provide a mobile-first translation system.

### Related Work
- **Bantupalli and Xie (2018):** Demonstrates how temporal models (RNNs) improve recognition accuracy over static classifiers by capturing motion dynamics.
- **Karthikeyan (2018):** Highlights strategies for on-device inference on mobile hardware, prioritizing latency and privacy.
- **Roh et al. (2021):** Emphasizes data collection as a core driver of ML performance, advocating for diverse and validated datasets.

---

## 5. Problem Definition
Existing ASL technologies are often fragmented, limited to alphabet letters, or reliant on cloud servers that introduce latency and privacy risks.

### Specific Goals and Deliverables
- **Goal:** Real-time on-device ASL recognition with natural sentence output.
- **Deliverables:** Functional Android prototype, TFLite recognition models, and a Firebase-backed contribution pipeline.

### Specifications and Constraints
- **Capabilities:** Real-time inference, multilingual support, and avatar rendering.
- **Constraints:** Initial vocabulary size, hardware limitations for complex models, and dependency on lighting/camera quality.

### Impact Analysis
- **Individual:** Improved independence and accessibility for ASL users.
- **Organizational:** Enhanced inclusivity in healthcare, education, and workplace environments.
- **Societal:** Increased awareness and adoption of assistive AI technologies globally.

---

## 6. Methodology
Gestura follows a hybrid edge-cloud architecture:
1. **On-Device:** CameraX captures frames; landmarks are extracted; TFLite classifies sequences into gloss.
2. **Cloud:** Firebase handles Auth, Firestore (metadata), and Storage (videos/models).
3. **NLP:** Gloss is sent to an LLM API to generate fluent sentences.

---

## 7. Concepts Considered
- **Development Environment:** Android Studio vs. Visual Studio Code.
- **Backend:** Firebase vs. Custom Node.js/FastAPI server.
- **ML Processing:** MediaPipe Holistic (landmarks) vs. Raw RGB 3D-CNN.
- **Networking:** Standard HttpUrlConnection vs. OkHttp with Extension Functions.

---

## 8. Concept Selection
- **IDE:** **Android Studio** was selected for its native Kotlin support and integrated debugging tools.
- **Backend:** **Firebase** was chosen for rapid development and built-in Auth/Storage scalability.
- **Processing:** **MediaPipe Holistic** landmarks were selected to reduce data dimensionality and improve privacy compared to raw video streaming.
- **Networking:** **OkHttp 4.x** with Kotlin extension functions was selected for modern, readable API handling.

---

## 9. Design and Implementation
The project implements a multi-tab Android interface:
- **ASL Tab:** Real-time inference using `AslSamplePipeline`.
- **Contribute Tab:** User-guided data capture with Firebase Storage uploads.
- **Avatar Tab:** `AvatarService` communicates with external APIs for 3D sign rendering.

### Code Snippet: Networking Extension
```kotlin
// Modern OkHttp usage with extension functions
.post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
```

---

## 10. Conclusion/Summary
Gestura successfully demonstrates the feasibility of a mobile-first, privacy-preserving ASL translation platform. By decoupling gesture recognition from linguistic generation and leveraging community-driven data, the app provides a scalable solution for inclusive communication.

---

## 12. Appendices
- [User Manual](UserManual.txt)
- [Project Wireframes](docs/justora-wireframe.svg)
- [System Architecture Diagram](docs/architecture.png)
