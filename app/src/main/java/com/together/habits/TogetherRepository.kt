package com.together.habits

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class TogetherRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    suspend fun ensureSignedIn(): String {
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    suspend fun linkGoogleAccount(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser
        return if (currentUser?.isAnonymous == true) {
            try {
                currentUser.linkWithCredential(credential).await().user!!.uid
            } catch (error: FirebaseAuthUserCollisionException) {
                auth.signInWithCredential(credential).await().user!!.uid
            }
        } else {
            auth.signInWithCredential(credential).await().user!!.uid
        }
    }

    fun observeMe(uid: String, onChange: (Person?, String?) -> Unit): ListenerRegistration =
        db.collection("users").document(uid).addSnapshotListener { document, error ->
            if (error != null) return@addSnapshotListener onChange(null, null)
            onChange(document?.toPerson(), document?.getString("partnershipId"))
        }

    suspend fun saveName(uid: String, name: String) {
        db.collection("users").document(uid).set(
            mapOf("name" to name.trim(), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
    }

    fun observePartnership(id: String, onChange: (Partnership?) -> Unit): ListenerRegistration =
        db.collection("partnerships").document(id).addSnapshotListener { doc, _ ->
            val members = doc?.get("memberIds") as? List<*> ?: emptyList<Any>()
            onChange(if (doc?.exists() == true) Partnership(doc.id, members.filterIsInstance<String>()) else null)
        }

    fun observeHabits(partnershipId: String, onChange: (List<Habit>) -> Unit): ListenerRegistration =
        db.collection("partnerships").document(partnershipId).collection("habits")
            .addSnapshotListener { value, _ ->
                onChange(value?.documents?.mapNotNull { it.toHabit() }?.sortedBy { it.title.lowercase() } ?: emptyList())
            }

    fun observeCompletions(partnershipId: String, date: String, onChange: (Set<String>) -> Unit): ListenerRegistration =
        db.collection("partnerships").document(partnershipId).collection("completions")
            .whereEqualTo("date", date).addSnapshotListener { value, _ ->
                onChange(value?.documents?.mapNotNull { doc ->
                    val habit = doc.getString("habitId") ?: return@mapNotNull null
                    val user = doc.getString("userId") ?: return@mapNotNull null
                    "$habit|$user|$date"
                }?.toSet() ?: emptySet())
            }

    suspend fun loadPeople(ids: List<String>): List<Person> = ids.mapNotNull { id ->
        db.collection("users").document(id).get().await().toPerson()
    }

    suspend fun createInvite(ownerId: String): String {
        val code = buildString { repeat(6) { append(alphabet[SecureRandom().nextInt(alphabet.length)]) } }
        db.collection("invites").document(code).set(mapOf(
            "ownerId" to ownerId,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp.now().seconds + 86_400
        )).await()
        return code
    }

    suspend fun joinWithCode(code: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("joinWithCode")
            .call(mapOf("code" to code.trim().uppercase())).await()
    }

    suspend fun leavePartnership(): Result<Unit> = runCatching {
        functions.getHttpsCallable("leavePartnership").call().await()
    }

    suspend fun addHabit(partnershipId: String, owner: Person, title: String, emoji: String) {
        db.collection("partnerships").document(partnershipId).collection("habits").add(mapOf(
            "title" to title.trim(), "emoji" to emoji.ifBlank { "✨" }, "ownerId" to owner.id,
            "ownerName" to owner.name, "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun toggleCompletion(partnershipId: String, habitId: String, userId: String, date: String, completed: Boolean) {
        val reference = db.collection("partnerships").document(partnershipId).collection("completions")
            .document("${habitId}_${userId}_$date")
        if (completed) reference.set(mapOf("habitId" to habitId, "userId" to userId, "date" to date, "completedAt" to FieldValue.serverTimestamp())).await()
        else reference.delete().await()
    }
}

private fun DocumentSnapshot.toPerson(): Person? =
    if (exists() && getString("name") != null) Person(id, getString("name")!!) else null

private fun DocumentSnapshot.toHabit(): Habit? {
    val title = getString("title") ?: return null
    val ownerId = getString("ownerId") ?: return null
    return Habit(id, title, getString("emoji") ?: "✨", ownerId, getString("ownerName") ?: "You")
}
