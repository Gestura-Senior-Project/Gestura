/* eslint-disable object-curly-spacing */
/* eslint-disable no-multi-spaces */
/* eslint-disable valid-jsdoc */
/* eslint-disable require-jsdoc */
/* eslint-disable max-len */

import {onCall, HttpsError} from "firebase-functions/v2/https";
import {initializeApp} from "firebase-admin/app";
import {getFirestore, FieldValue} from "firebase-admin/firestore";

const app = initializeApp();
const db = getFirestore(app);

interface AslSamplePayload {
  word: string;           // requested word
  predictedLabel: string; // model output label
  confidence: number;     // 0–100
  keypoints: number[];    // flattened mediapipe vector
  userEmail: string;      // user email (you already have it)
}

interface SubmitResponse {
  status: "accepted" | "review";
  collection: "asl_accepted" | "asl_review";
  id: string;
}

function isKnownByModel(word: string, predictedLabel: string): boolean {
  if (!predictedLabel) return false;
  return predictedLabel.trim().toLowerCase() === word.trim().toLowerCase();
}

export const submitAslSample = onCall(
  {region: "us-central1"},
  async (request): Promise<SubmitResponse> => {
    // Auth required
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const data = request.data as AslSamplePayload;

    const word = data.word?.trim();
    const predictedLabel = data.predictedLabel?.trim();
    const confidence = data.confidence;
    const keypoints = data.keypoints;
    const userEmail = data.userEmail?.trim();

    if (!word) {
      throw new HttpsError("invalid-argument", "word is required");
    }

    if (typeof confidence !== "number" || confidence < 0 || confidence > 100) {
      throw new HttpsError(
        "invalid-argument",
        "confidence must be a number between 0 and 100"
      );
    }

    if (!Array.isArray(keypoints) || keypoints.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "keypoints array is required"
      );
    }

    if (!userEmail) {
      throw new HttpsError("invalid-argument", "userEmail is required");
    }

    // "Known" = model label matches requested word (case-insensitive)
    const known = isKnownByModel(word, predictedLabel || "");

    let collectionName: "asl_accepted" | "asl_review";
    let status: "accepted" | "review";

    if (known && confidence >= 90) {
      // High confidence AND known word → accepted
      collectionName = "asl_accepted";
      status = "accepted";
    } else {
      // 75–85 range, low confidence, or unseen word → review
      collectionName = "asl_review";
      status = "review";
    }

    const docRef = db.collection(collectionName).doc();

    const payload = {
      id: docRef.id,
      userEmail,
      word,
      predictedLabel: predictedLabel || null,
      confidence,
      keypoints,
      status,
      createdAt: FieldValue.serverTimestamp(),
    };

    await docRef.set(payload);

    return {
      status,
      collection: collectionName,
      id: docRef.id,
    };
  }
);
