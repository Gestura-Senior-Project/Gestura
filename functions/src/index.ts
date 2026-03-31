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
  typedWord: string;
  predictedLabel: string;
  confidence: number;     // 0-100
  keypoints: number[];
  userEmail: string;
  videoUrl?: string;
  videoStoragePath?: string;
  isMismatch?: boolean;
}

interface SubmitResponse {
  status: "accepted" | "review";
  collection: "asl_accepted" | "asl_review";
  id: string;
}

function normalizeWord(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, "_");
}

export const submitAslSample = onCall(
  {region: "us-central1"},
  async (request): Promise<SubmitResponse> => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const data = request.data as AslSamplePayload;

    const typedWord = data.typedWord?.trim();
    const predictedLabel = data.predictedLabel?.trim();
    const confidence = data.confidence;
    const keypoints = data.keypoints;
    const userEmail = data.userEmail?.trim();
    const videoUrl = data.videoUrl?.trim();
    const videoStoragePath = data.videoStoragePath?.trim();

    if (!typedWord) {
      throw new HttpsError("invalid-argument", "typedWord is required");
    }

    if (!predictedLabel) {
      throw new HttpsError("invalid-argument", "predictedLabel is required");
    }

    if (typeof confidence !== "number" || confidence < 0 || confidence > 100) {
      throw new HttpsError(
        "invalid-argument",
        "confidence must be a number between 0 and 100",
      );
    }

    if (!Array.isArray(keypoints) || keypoints.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "keypoints array is required",
      );
    }

    if (!userEmail) {
      throw new HttpsError("invalid-argument", "userEmail is required");
    }

    const typedNorm = normalizeWord(typedWord);
    const predictedNorm = normalizeWord(predictedLabel);

    const isMismatch = typedNorm !== predictedNorm;
    const lowConfidence = confidence < 90;

    let collectionName: "asl_accepted" | "asl_review";
    let status: "accepted" | "review";

    if (isMismatch || lowConfidence) {
      collectionName = "asl_review";
      status = "review";
    } else {
      collectionName = "asl_accepted";
      status = "accepted";
    }

    const docRef = db.collection(collectionName).doc();

    if (collectionName === "asl_review") {
      await docRef.set({
        id: docRef.id,
        userEmail,
        word: typedWord,
        typedWord,
        predictedLabel,
        confidence,
        keypoints,
        videoUrl: videoUrl || null,
        videoStoragePath: videoStoragePath || null,
        status,
        createdAt: FieldValue.serverTimestamp(),
      });
    } else {
      await docRef.set({
        id: docRef.id,
        userEmail,
        word: typedWord,
        predictedLabel,
        confidence,
        keypoints,
        status,
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    return {
      status,
      collection: collectionName,
      id: docRef.id,
    };
  },
);
