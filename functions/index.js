const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const storage = admin.storage();

const EXPIRY_BATCH_LIMIT = 200;

/**
 * Deletes subcollections (extracted, explanations, qa), private Firebase Storage
 * files under users/{uid}/reports/{reportId}/, and the parent report document.
 * Explicitly leaves user profiles, family profiles, and reminders untouched.
 */
async function deleteReportAndAssets(reportRef, ownerUid, reportId, storageObjectPath) {
  const bucket = storage.bucket();

  // Delete known report subcollections first
  const subcollections = ["extracted", "explanations", "qa"];
  for (const subName of subcollections) {
    const subSnap = await reportRef.collection(subName).get();
    if (!subSnap.empty) {
      const batch = db.batch();
      subSnap.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
  }

  // Delete original binary and any prefix artifacts from private Firebase Storage
  const canonicalPath = storageObjectPath || `users/${ownerUid}/reports/${reportId}/original`;
  try {
    await bucket.file(canonicalPath).delete({ ignoreNotFound: true });
    await bucket.deleteFiles({
      prefix: `users/${ownerUid}/reports/${reportId}/`,
      force: true
    });
  } catch (err) {
    logger.warn("Storage cleanup encountered an error during report deletion", {
      errorType: err && err.name ? err.name : "StorageError"
    });
  }

  // Remove parent report document if still present
  await reportRef.delete();
}

/**
 * Scheduled Cloud Function running hourly to enforce the 7-day medical report retention policy
 * server-side across all users, regardless of whether clients are online.
 */
exports.cleanupExpiredMedicalReports = onSchedule(
  {
    schedule: "every 60 minutes",
    timeZone: "UTC",
    memory: "256MiB",
    timeoutSeconds: 300
  },
  async () => {
    const nowTimestamp = admin.firestore.Timestamp.now();
    let totalPurged = 0;

    while (true) {
      const expiredSnap = await db
        .collectionGroup("reports")
        .where("expiresAt", "<=", nowTimestamp)
        .limit(EXPIRY_BATCH_LIMIT)
        .get();

      if (expiredSnap.empty) {
        break;
      }

      for (const doc of expiredSnap.docs) {
        const data = doc.data() || {};
        const parentUserRef = doc.ref.parent && doc.ref.parent.parent;
        const ownerUid = data.ownerUid || (parentUserRef ? parentUserRef.id : null);
        if (!ownerUid) {
          continue;
        }
        await deleteReportAndAssets(
          doc.ref,
          ownerUid,
          doc.id,
          data.storageObjectPath
        );
        totalPurged += 1;
      }

      if (expiredSnap.size < EXPIRY_BATCH_LIMIT) {
        break;
      }
    }

    logger.info("Scheduled 7-day report expiry sweep finished", { totalPurged });
  }
);

/**
 * Firestore trigger ensuring that whenever a parent report document is deleted
 * (by client Keep->Delete action, exit default-delete, or TTL/manual purge),
 * all residual subcollections and private Storage binaries are also removed.
 */
exports.onMedicalReportDeleted = onDocumentDeleted(
  {
    document: "users/{userId}/reports/{reportId}",
    memory: "256MiB",
    timeoutSeconds: 120
  },
  async (event) => {
    const { userId, reportId } = event.params;
    const deletedData = event.data && event.data.data ? event.data.data() : {};
    const reportRef = db
      .collection("users")
      .doc(userId)
      .collection("reports")
      .doc(reportId);

    const subcollections = ["extracted", "explanations", "qa"];
    for (const subName of subcollections) {
      const subSnap = await reportRef.collection(subName).get();
      if (!subSnap.empty) {
        const batch = db.batch();
        subSnap.docs.forEach((doc) => batch.delete(doc.ref));
        await batch.commit();
      }
    }

    const bucket = storage.bucket();
    const canonicalPath =
      (deletedData && deletedData.storageObjectPath) ||
      `users/${userId}/reports/${reportId}/original`;

    try {
      await bucket.file(canonicalPath).delete({ ignoreNotFound: true });
      await bucket.deleteFiles({
        prefix: `users/${userId}/reports/${reportId}/`,
        force: true
      });
    } catch (err) {
      logger.warn("Storage cleanup in onMedicalReportDeleted failed", {
        errorType: err && err.name ? err.name : "StorageError"
      });
    }
  }
);
