const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();
const db = getFirestore();

/** Atomically turns a valid, one-time code into a two-person partnership. */
exports.joinWithCode = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in before pairing.");
  const joinerId = request.auth.uid;
  const code = String(request.data?.code || "").trim().toUpperCase();
  if (!/^[A-Z2-9]{6}$/.test(code)) throw new HttpsError("invalid-argument", "Enter a valid pairing code.");

  await db.runTransaction(async (transaction) => {
    const inviteRef = db.collection("invites").doc(code);
    const invite = await transaction.get(inviteRef);
    if (!invite.exists) throw new HttpsError("not-found", "That pairing code was not found.");
    const ownerId = invite.get("ownerId");
    if (ownerId === joinerId) throw new HttpsError("failed-precondition", "Ask your partner to use this code on their phone.");
    if (Number(invite.get("expiresAt") || 0) <= Math.floor(Date.now() / 1000)) {
      throw new HttpsError("deadline-exceeded", "That pairing code has expired.");
    }

    const ownerRef = db.collection("users").doc(ownerId);
    const joinerRef = db.collection("users").doc(joinerId);
    const [owner, joiner] = await Promise.all([transaction.get(ownerRef), transaction.get(joinerRef)]);
    if (!owner.exists || !joiner.exists) throw new HttpsError("failed-precondition", "Both partners need to finish setup first.");
    if (owner.get("partnershipId") || joiner.get("partnershipId")) throw new HttpsError("already-exists", "One of these accounts is already paired.");

    const partnershipRef = db.collection("partnerships").doc();
    transaction.set(partnershipRef, { memberIds: [ownerId, joinerId], createdAt: FieldValue.serverTimestamp() });
    transaction.update(ownerRef, { partnershipId: partnershipRef.id, updatedAt: FieldValue.serverTimestamp() });
    transaction.update(joinerRef, { partnershipId: partnershipRef.id, updatedAt: FieldValue.serverTimestamp() });
    transaction.delete(inviteRef);
  });
  return { paired: true };
});

/** Removes the caller from their partnership without deleting shared history. */
exports.leavePartnership = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in before leaving a partnership.");
  const userId = request.auth.uid;
  const userRef = db.collection("users").doc(userId);

  await db.runTransaction(async (transaction) => {
    const user = await transaction.get(userRef);
    const partnershipId = user.get("partnershipId");
    if (!partnershipId) return;

    const partnershipRef = db.collection("partnerships").doc(partnershipId);
    const partnership = await transaction.get(partnershipRef);
    const memberIds = partnership.exists ? partnership.get("memberIds") || [] : [];

    transaction.update(userRef, {
      partnershipId: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    if (partnership.exists) {
      transaction.update(partnershipRef, {
        memberIds: memberIds.filter((memberId) => memberId !== userId),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  });

  return { left: true };
});
