package com.together.habits

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private val Lavender = Color(0xFF6750A4)
private val Lilac = Color(0xFFF0E7FF)
private val Ink = Color(0xFF201A30)

private val lightColors = lightColorScheme(
    primary = Lavender,
    onPrimary = Color.White,
    primaryContainer = Lilac,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF7D5260),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFF1EDF4),
    onSurfaceVariant = Color(0xFF625B71)
)
private val darkColors = darkColorScheme(
    primary = Color(0xFFD8B4FE),
    onPrimary = Color(0xFF38204F),
    primaryContainer = Color(0xFF553477),
    onPrimaryContainer = Color(0xFFF1DFFF),

    secondary = Color(0xFFA8DADC),
    onSecondary = Color(0xFF123337),
    secondaryContainer = Color(0xFF234A4E),
    onSecondaryContainer = Color(0xFFC0F0F1),

    background = Color(0xFF121318),
    onBackground = Color(0xFFE7E1EA),

    surface = Color(0xFF191A21),
    onSurface = Color(0xFFE7E1EA),

    surfaceVariant = Color(0xFF2B2D37),
    onSurfaceVariant = Color(0xFFC9C1D0),

    outline = Color(0xFF938B9B),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            TogetherTheme(themeMode) {
                TogetherApp(themeMode, onThemeModeChange = { themeMode = it })
            }
        }
    }
}

@Composable private fun TogetherTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    var colorScheme = if (darkTheme) darkColors else lightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable private fun TogetherApp(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, model: HabitsViewModel = viewModel()) {
    val state by model.state
    val context = LocalContext.current
    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        }.onSuccess { account ->
            val idToken = account.idToken
            if (idToken == null) model.showError("Google did not return an ID token.")
            else model.signInWithGoogle(idToken, account.displayName)
        }
            .onFailure { model.showError("Couldn’t complete Google sign-in.") }
    }
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.me == null -> WelcomeScreen(
                    onContinue = model::saveName,
                    onGoogleSignIn = { googleSignInLauncher.launch(googleSignInClient.signInIntent) }
                )
                state.partnership == null -> PairingScreen(state, model::createInvite, model::join)
                else -> Dashboard(state, model::addHabit, model::toggle, model::leavePartnership)
            }
            ThemeToggleButton(
                isDark = isDark,
                onToggle = { onThemeModeChange(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 12.dp)
            )
        }
    }
    state.error?.let { message ->
        AlertDialog(onDismissRequest = model::clearError, confirmButton = { TextButton(model::clearError) { Text("OK") } }, title = { Text("Something needs attention") }, text = { Text(message) })
    }
}

@Composable private fun ThemeToggleButton(isDark: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (isDark) "Switch to light theme" else "Switch to dark theme"
        )
    }
}

@Composable private fun WelcomeScreen(onContinue: (String) -> Unit, onGoogleSignIn: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(24.dp))
        Text("Better habits, together.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        Text("Keep the promises you make to yourself — with a little loving accountability from your person.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(34.dp))
        OutlinedTextField(name, { name = it }, label = { Text("What should your partner call you?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button({ onContinue(name) }, Modifier.fillMaxWidth().height(52.dp), enabled = name.trim().length >= 2) { Text("Get started") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onGoogleSignIn, Modifier.fillMaxWidth().height(52.dp)) { Text("Continue with Google") }
    }
}

@Composable private fun PairingScreen(state: AppState, onCreateInvite: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(52.dp))
        Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(18.dp))
        Text("Invite your accountability partner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Pair once, then each of you can see today’s progress in real time.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        if (state.inviteCode == null) {
            Button(onCreateInvite, Modifier.fillMaxWidth().height(52.dp)) { Text("Create a pairing code") }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Send this to your partner", style = MaterialTheme.typography.labelLarge)
                    Text(state.inviteCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                    Text("Expires in 24 hours", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("or join their space", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(code, { code = it.uppercase().take(6) }, label = { Text("6-character code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedButton({ onJoin(code) }, Modifier.fillMaxWidth(), enabled = code.length == 6) { Text("Join partner") }
    }
}

@Composable private fun Dashboard(state: AppState, onAddHabit: (String, String) -> Unit, onToggle: (Habit) -> Unit, onLeavePartnership: () -> Unit) {
    var addHabit by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    val me = state.me!!
    val partner = state.partner
    val myDone = state.habits.count { "${it.id}|${me.id}|${today()}" in state.completions }
    val partnerDone = partner?.let { person -> state.habits.count { "${it.id}|${person.id}|${today()}" in state.completions } } ?: 0
    Scaffold(floatingActionButton = { FloatingActionButton({ addHabit = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Default.Add, "Add habit") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).padding(top = 56.dp), contentPadding = PaddingValues(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Good morning, ${me.name}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("${today().replace("-", " · ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text("You & ${partner?.name ?: "your partner"}", fontWeight = FontWeight.Bold); Text("A shared place to show up") }
                        Text("$myDone/${state.habits.count()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item { Text("Today’s habits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.habits.isEmpty()) item { EmptyHabits() }
            items(state.habits, key = { it.id }) { habit ->
                HabitCard(habit, me, partner, state.completions, onToggle)
            }
            if (partner != null) item {
                Text("${partner.name} has checked in on $partnerDone habit${if (partnerDone == 1) "" else "s"} today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 76.dp))
            }
            item {
                TextButton(onClick = { showLeaveConfirmation = true }, modifier = Modifier.padding(bottom = 72.dp)) {
                    Text("Leave partnership", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (addHabit) AddHabitDialog(onDismiss = { addHabit = false }, onAdd = { title, emoji -> onAddHabit(title, emoji); addHabit = false })
    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave partnership?") },
            text = { Text("You will stop seeing shared habits and check-ins. Your existing history will remain available if you pair again later.") },
            dismissButton = { TextButton(onClick = { showLeaveConfirmation = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = { showLeaveConfirmation = false; onLeavePartnership() }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable private fun HabitCard(habit: Habit, me: Person, partner: Person?, completions: Set<String>, onToggle: (Habit) -> Unit) {
    val mine = habit.ownerId == me.id
    val myChecked = "${habit.id}|${me.id}|${today()}" in completions
    val partnerChecked = partner?.let { "${habit.id}|${it.id}|${today()}" in completions } == true
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (myChecked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(habit.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (mine) "Your habit" else "${habit.ownerName}’s habit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                partner?.let { Text("${it.name}: ${if (partnerChecked) "checked in ✓" else "not yet"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
            Checkbox(checked = myChecked, onCheckedChange = if (mine) { { _ -> onToggle(habit) } } else null)
        }
    }
}

@Composable private fun EmptyHabits() = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Start with one promise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Tap + to add a habit you want your partner to help you keep.", textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable private fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var emoji by remember { mutableStateOf("✨") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("A new habit") }, text = {
        Column { OutlinedTextField(title, { title = it }, label = { Text("Habit") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(emoji, { emoji = it.take(2) }, label = { Text("Icon") }, singleLine = true) }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } }, confirmButton = { TextButton({ onAdd(title, emoji) }, enabled = title.isNotBlank()) { Text("Add") } })
}

