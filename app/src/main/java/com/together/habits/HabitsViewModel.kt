package com.together.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.time.LocalDate

class HabitsViewModel : ViewModel() {
    private val repository = TogetherRepository()
    private var uid = ""
    private var meListener: ListenerRegistration? = null
    private var partnershipListener: ListenerRegistration? = null
    private var habitsListener: ListenerRegistration? = null
    private var completionsListener: ListenerRegistration? = null
    private var observedPartnership = ""

    var state = androidx.compose.runtime.mutableStateOf(AppState())
        private set

    init {
        viewModelScope.launch {
            runCatching { repository.ensureSignedIn() }
                .onSuccess { signedInId -> uid = signedInId; watchMe() }
                .onFailure { fail("Couldn’t sign in. Check your Firebase setup and connection.") }
        }
    }

    fun saveName(name: String) = viewModelScope.launch {
        if (name.trim().length < 2) return@launch fail("Please enter a name with at least 2 characters.")
        runCatching { repository.saveName(uid, name) }.onFailure { fail("Couldn’t save your name.") }
    }

    fun signInWithGoogle(idToken: String, displayName: String?) = viewModelScope.launch {
        runCatching {
            val signedInId = repository.linkGoogleAccount(idToken)
            uid = signedInId
            meListener?.remove()
            meListener = null
            stopPartnershipListeners()
            state.value = state.value.copy(loading = true, me = null, partnership = null, partner = null, habits = emptyList(), completions = emptySet())
            if (!displayName.isNullOrBlank()) repository.saveName(signedInId, displayName)
            watchMe()
        }
            .onFailure { fail(googleAuthError(it)) }
    }

    fun createInvite() = viewModelScope.launch {
        runCatching { repository.createInvite(uid) }
            .onSuccess { state.value = state.value.copy(inviteCode = it, error = null) }
            .onFailure { fail("Couldn’t create a pairing code.") }
    }

    fun join(code: String) = viewModelScope.launch {
        if (code.trim().length != 6) return@launch fail("Enter the 6-character pairing code.")
        repository.joinWithCode(code).onSuccess { state.value = state.value.copy(error = null) }
            .onFailure { fail(it.message ?: "Couldn’t pair your accounts.") }
    }

    fun leavePartnership() = viewModelScope.launch {
        repository.leavePartnership()
            .onSuccess {
                observedPartnership = ""
                partnershipListener?.remove(); habitsListener?.remove(); completionsListener?.remove()
                state.value = state.value.copy(partnership = null, partner = null, habits = emptyList(), completions = emptySet(), inviteCode = null, error = null)
            }
            .onFailure { fail("Couldn’t leave this partnership.") }
    }

    fun addHabit(title: String, emoji: String) = viewModelScope.launch {
        val snapshot = state.value
        val partnershipId = snapshot.partnership?.id ?: return@launch
        if (title.isBlank()) return@launch fail("Give this habit a name first.")
        runCatching { repository.addHabit(partnershipId, snapshot.me ?: return@launch, title, emoji) }
            .onFailure { fail("Couldn’t add the habit.") }
    }

    fun toggle(habit: Habit) = viewModelScope.launch {
        val snapshot = state.value
        val partnershipId = snapshot.partnership?.id ?: return@launch
        val currentUser = snapshot.me?.id ?: return@launch
        val date = today()
        val checked = "${habit.id}|$currentUser|$date" in snapshot.completions
        runCatching { repository.toggleCompletion(partnershipId, habit.id, currentUser, date, !checked) }
            .onFailure { fail("Couldn’t update that check-in.") }
    }

    fun clearError() { state.value = state.value.copy(error = null) }

    fun showError(message: String) { fail(message) }

    private fun googleAuthError(error: Throwable): String = when ((error as? FirebaseAuthException)?.errorCode) {
        "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "That Google account is already connected to another Together account. Use the Google account you originally connected."
        "ERROR_OPERATION_NOT_ALLOWED" ->
            "Google sign-in is not enabled in Firebase Authentication yet."
        "ERROR_INVALID_CREDENTIAL" ->
            "Google sign-in returned an invalid credential. Check that the app uses the latest Firebase configuration."
        "ERROR_NETWORK_REQUEST_FAILED" ->
            "Google sign-in needs an internet connection."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Couldn’t connect your Google account."
    }

    private fun watchMe() {
        meListener = repository.observeMe(uid) { person, partnershipId ->
            state.value = state.value.copy(loading = false, me = person)
            if (partnershipId == null) {
                stopPartnershipListeners()
                state.value = state.value.copy(partnership = null, partner = null, habits = emptyList(), completions = emptySet())
            } else if (partnershipId != observedPartnership) watchPartnership(partnershipId)
        }
    }

    private fun stopPartnershipListeners() {
        partnershipListener?.remove(); habitsListener?.remove(); completionsListener?.remove()
        partnershipListener = null
        habitsListener = null
        completionsListener = null
        observedPartnership = ""
    }

    private fun watchPartnership(id: String) {
        observedPartnership = id
        partnershipListener?.remove(); habitsListener?.remove(); completionsListener?.remove()
        partnershipListener = repository.observePartnership(id) { partnership ->
            state.value = state.value.copy(partnership = partnership)
            if (partnership != null) {
                viewModelScope.launch {
                    val people = repository.loadPeople(partnership.memberIds)
                    state.value = state.value.copy(partner = people.firstOrNull { it.id != uid })
                }
            }
        }
        habitsListener = repository.observeHabits(id) { habits -> state.value = state.value.copy(habits = habits) }
        completionsListener = repository.observeCompletions(id, today()) { completions -> state.value = state.value.copy(completions = completions) }
    }

    private fun fail(message: String) { state.value = state.value.copy(loading = false, error = message) }
    override fun onCleared() { meListener?.remove(); partnershipListener?.remove(); habitsListener?.remove(); completionsListener?.remove() }
}

fun today(): String = LocalDate.now().toString()
