package com.together.habits

data class Person(val id: String, val name: String)

data class Partnership(val id: String, val memberIds: List<String>)

data class Habit(
    val id: String,
    val title: String,
    val emoji: String,
    val ownerId: String,
    val ownerName: String
)

data class Completion(val habitId: String, val userId: String, val date: String)

data class AppState(
    val loading: Boolean = true,
    val me: Person? = null,
    val partner: Person? = null,
    val partnership: Partnership? = null,
    val habits: List<Habit> = emptyList(),
    val completions: Set<String> = emptySet(),
    val inviteCode: String? = null,
    val error: String? = null
)

fun Completion.key() = "$habitId|$userId|$date"
