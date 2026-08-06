package com.example.sidequest

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sidequest.data.ChallengeWithAssignment
import com.example.sidequest.data.FirebaseAuthRepository
import com.example.sidequest.data.FirebaseChallengeRepository
import com.example.sidequest.data.FirebaseGroupRepository
import com.example.sidequest.data.FirebaseMatchRepository
import com.example.sidequest.ui.auth.AuthViewModel
import com.example.sidequest.ui.group.GroupViewModel
import com.example.sidequest.ui.match.MatchViewModel
import com.example.sidequest.ui.theme.SideQuestTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

sealed class Screen(val route: String) {
    object Landing : Screen("landing")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object GroupDetail : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: String) = "group_detail/$groupId"
    }
    object CreateMatch : Screen("create_match/{groupId}") {
        fun createRoute(groupId: String) = "create_match/$groupId"
    }
    object ActiveMatch : Screen("active_match/{groupId}/{matchId}") {
        fun createRoute(groupId: String, matchId: String) = "active_match/$groupId/$matchId"
    }
    object MyChallenges : Screen("my_challenges/{groupId}/{matchId}") {
        fun createRoute(groupId: String, matchId: String) = "my_challenges/$groupId/$matchId"
    }
    object ChallengeDetails : Screen("challenge_details/{groupId}/{matchId}/{assignmentId}") {
        fun createRoute(groupId: String, matchId: String, assignmentId: String) = 
            "challenge_details/$groupId/$matchId/$assignmentId"
    }
}

class AuthViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(
                FirebaseAuthRepository(),
                FirebaseGroupRepository(),
                FirebaseChallengeRepository()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GroupViewModelFactory(private val groupId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GroupViewModel(
                groupId,
                FirebaseGroupRepository(),
                FirebaseAuthRepository()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MatchViewModelFactory(
    private val groupId: String,
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MatchViewModel(
                groupId,
                FirebaseMatchRepository(),
                FirebaseChallengeRepository(),
                userId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SideQuestTheme {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory())
                val authState by authViewModel.authState.collectAsState()
                val navController = rememberNavController()

                // Determine initial screen to avoid flicker
                val initialScreen = remember {
                    if (authViewModel.authState.value.user != null) Screen.Home.route else Screen.Landing.route
                }

                // Navigation based on auth state
                LaunchedEffect(authState.user) {
                    val currentRoute = navController.currentDestination?.route
                    if (authState.user != null && (currentRoute == Screen.Landing.route || currentRoute == Screen.Login.route || currentRoute == Screen.Register.route)) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else if (authState.user == null && currentRoute != Screen.Landing.route) {
                        navController.navigate(Screen.Landing.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                // Navigation based on group creation
                LaunchedEffect(authState.groupCreatedId) {
                    authState.groupCreatedId?.let { groupId ->
                        navController.navigate(Screen.GroupDetail.createRoute(groupId)) {
                            popUpTo(Screen.Home.route)
                        }
                        authViewModel.resetGroupCreationState()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SideQuestNavHost(
                        navController = navController,
                        authViewModel = authViewModel,
                        startDestination = initialScreen,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun SideQuestNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()

    // Google Sign-In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("YOUR_WEB_CLIENT_ID") // TODO: Replace with your actual web client ID from Firebase Console
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
            authViewModel.signInWithGoogle(credential)
        } catch (e: ApiException) {
            Toast.makeText(context, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Landing.route) {
            LandingPage(
                onLoginClick = { navController.navigate(Screen.Login.route) },
                onRegisterClick = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Login.route) {
            LoginPage(
                authState = authState,
                onLoginClick = { email, password -> authViewModel.login(email, password) },
                onGoogleSignInClick = { launcher.launch(googleSignInClient.signInIntent) },
                onRegisterClick = { 
                    navController.navigate(Screen.Register.route) {
                        popUpTo(Screen.Landing.route)
                    }
                },
                onErrorDismiss = { authViewModel.clearError() }
            )
        }
        composable(Screen.Register.route) {
            RegisterPage(
                authState = authState,
                onRegisterClick = { username, email, password -> 
                    authViewModel.register(username, email, password) 
                },
                onLoginClick = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Landing.route)
                    }
                },
                onErrorDismiss = { authViewModel.clearError() }
            )
        }
        composable(Screen.Home.route) { 
            HomePage(
                authState = authState,
                onLogoutClick = { authViewModel.logout() },
                onCreateGroupClick = { authViewModel.createGroup() },
                onJoinGroupClick = { inviteCode -> authViewModel.joinGroup(inviteCode) },
                onSeedChallengesClick = { 
                    val jsonString = context.assets.open("challenges.json").bufferedReader().use { it.readText() }
                    authViewModel.seedDefaultChallenges(jsonString)
                }
            ) 
        }
        composable(Screen.GroupDetail.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupViewModel: GroupViewModel = viewModel(factory = GroupViewModelFactory(groupId))
            val groupState by groupViewModel.state.collectAsState()
            
            val matchViewModel: MatchViewModel = viewModel(
                factory = MatchViewModelFactory(groupId, authState.user?.uid ?: "")
            )
            val matchState by matchViewModel.state.collectAsState()

            GroupDetailScreen(
                groupId = groupId,
                authState = authState,
                groupState = groupState,
                matchState = matchState,
                onBackClick = { navController.popBackStack() },
                onLeaveGroupClick = { authViewModel.leaveGroup(groupId) },
                onStartMatchClick = { navController.navigate(Screen.CreateMatch.createRoute(groupId)) },
                onGoToMatchClick = { matchId -> 
                    navController.navigate(Screen.ActiveMatch.createRoute(groupId, matchId))
                }
            )
        }
        composable(Screen.CreateMatch.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val matchViewModel: MatchViewModel = viewModel(
                factory = MatchViewModelFactory(groupId, authState.user?.uid ?: "")
            )
            val matchState by matchViewModel.state.collectAsState()

            CreateMatchScreen(
                groupId = groupId,
                matchState = matchState,
                onBackClick = { navController.popBackStack() },
                onCreateMatchClick = { count -> matchViewModel.createMatch(count) },
                onMatchCreated = { matchId ->
                    navController.navigate(Screen.ActiveMatch.createRoute(groupId, matchId)) {
                        popUpTo(Screen.GroupDetail.createRoute(groupId)) { inclusive = false }
                    }
                    matchViewModel.resetMatchCreationState()
                }
            )
        }
        composable(Screen.ActiveMatch.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
            val matchViewModel: MatchViewModel = viewModel(
                factory = MatchViewModelFactory(groupId, authState.user?.uid ?: "")
            )
            val matchState by matchViewModel.state.collectAsState()

            ActiveMatchScreen(
                matchId = matchId,
                matchState = matchState,
                onBackClick = { navController.popBackStack() },
                onViewChallengesClick = { 
                    navController.navigate(Screen.MyChallenges.createRoute(groupId, matchId))
                }
            )
        }
        composable(Screen.MyChallenges.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
            val matchViewModel: MatchViewModel = viewModel(
                factory = MatchViewModelFactory(groupId, authState.user?.uid ?: "")
            )
            val matchState by matchViewModel.state.collectAsState()

            MyChallengesScreen(
                matchState = matchState,
                onBackClick = { navController.popBackStack() },
                onChallengeClick = { assignmentId ->
                    navController.navigate(Screen.ChallengeDetails.createRoute(groupId, matchId, assignmentId))
                }
            )
        }
        composable(Screen.ChallengeDetails.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: ""
            val matchViewModel: MatchViewModel = viewModel(
                factory = MatchViewModelFactory(groupId, authState.user?.uid ?: "")
            )
            val matchState by matchViewModel.state.collectAsState()

            // Initialize selected challenge in VM
            LaunchedEffect(assignmentId) {
                matchViewModel.selectChallenge(assignmentId)
            }

            ChallengeDetailsScreen(
                matchState = matchState,
                onBackClick = { navController.popBackStack() },
                onSubmitProofClick = { /* TODO */ }
            )
        }
    }
}

@Composable
fun LandingPage(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sidequests",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log In")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }
    }
}

@Composable
fun LoginPage(
    authState: com.example.sidequest.ui.auth.AuthState,
    onLoginClick: (String, String) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(authState.error) {
        authState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onErrorDismiss()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !authState.isLoading
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !authState.isLoading
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = { /* TODO */ },
                modifier = Modifier.align(Alignment.End),
                enabled = !authState.isLoading
            ) {
                Text("Forgot Password?")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onLoginClick(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                Text("Log In")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onGoogleSignInClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Placeholder for Google Icon
                    Text("Sign in with Google")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onRegisterClick, enabled = !authState.isLoading) {
                Text("Don't have an account? Register")
            }
        }

        if (authState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun RegisterPage(
    authState: com.example.sidequest.ui.auth.AuthState,
    onRegisterClick: (String, String, String) -> Unit,
    onLoginClick: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(authState.error) {
        authState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onErrorDismiss()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Join Sidequests",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !authState.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !authState.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !authState.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !authState.isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onRegisterClick(username, email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading && 
                        username.isNotBlank() && 
                        email.isNotBlank() && 
                        password.isNotBlank() && 
                        password == confirmPassword
            ) {
                Text("Create Account")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onLoginClick, enabled = !authState.isLoading) {
                Text("Already have an account? Log In")
            }
        }

        if (authState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun HomePage(
    authState: com.example.sidequest.ui.auth.AuthState,
    onLogoutClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onJoinGroupClick: (String) -> Unit,
    onSeedChallengesClick: () -> Unit
) {
    val metadata = authState.userMetadata
    var showJoinDialog by remember { mutableStateOf(false) }

    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { inviteCode ->
                onJoinGroupClick(inviteCode)
                showJoinDialog = false
            },
            isLoading = authState.isJoiningGroup
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Welcome, ${metadata?.username ?: "Adventurer"}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = metadata?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = if (metadata?.groupId != null) "Current Group: ${metadata.groupId}" else "You are not in a group",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (metadata?.groupId == null) {
                Button(
                    onClick = onCreateGroupClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !authState.isCreatingGroup && !authState.isJoiningGroup
                ) {
                    Text("Create Group")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !authState.isCreatingGroup && !authState.isJoiningGroup
                ) {
                    Text("Join Group")
                }
            } else {
                Button(
                    onClick = { /* TODO: Navigate to group details if not already navigated */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to Group")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Temporary Seeding Button (Visible for development)
            TextButton(
                onClick = onSeedChallengesClick,
                enabled = !authState.isSeeding
            ) {
                Text(if (authState.isSeeding) "Seeding Challenges..." else "Seed Default Challenges")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isCreatingGroup && !authState.isJoiningGroup
            ) {
                Text("Logout")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (authState.isCreatingGroup || authState.isJoiningGroup) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    isLoading: Boolean
) {
    var inviteCode by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Join a Group", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text("Invite Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isLoading) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onJoin(inviteCode) },
                            enabled = !isLoading && inviteCode.length == 8
                        ) {
                            Text("Join")
                        }
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun GroupDetailScreen(
    groupId: String,
    authState: com.example.sidequest.ui.auth.AuthState,
    groupState: com.example.sidequest.ui.group.GroupState,
    matchState: com.example.sidequest.ui.match.MatchState,
    onBackClick: () -> Unit,
    onLeaveGroupClick: () -> Unit,
    onStartMatchClick: () -> Unit,
    onGoToMatchClick: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(authState.error) {
        authState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    // Navigate back to home if user is no longer in a group
    LaunchedEffect(authState.userMetadata?.groupId) {
        if (authState.userMetadata?.groupId == null) {
            onBackClick()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Group Details", style = MaterialTheme.typography.headlineLarge)
            
            if (groupState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else if (groupState.error != null) {
                Text(text = groupState.error, color = MaterialTheme.colorScheme.error)
            } else {
                val group = groupState.group
                Text(text = "ID: $groupId", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Invite Code: ${group?.inviteCode ?: "N/A"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (matchState.activeMatch != null) "Status: Match Active" else "Status: Waiting",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (matchState.activeMatch != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    if (matchState.activeMatch != null) {
                        Button(onClick = { onGoToMatchClick(matchState.activeMatch.id) }) {
                            Text("Go to Match")
                        }
                    } else if (group?.ownerId == authState.user?.uid) {
                        Button(onClick = onStartMatchClick) {
                            Text("Start New Match")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Members (${groupState.members.size}/10)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Start)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    groupState.members.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Placeholder for profile photo
                                Text(text = member.username.take(1).uppercase())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = member.username, style = MaterialTheme.typography.bodyLarge)
                                if (member.uid == group?.ownerId) {
                                    Text(
                                        text = "Owner",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onLeaveGroupClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLeavingGroup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Leave Group")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLeavingGroup
            ) {
                Text("Back to Home")
            }
        }

        if (authState.isLeavingGroup) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun CreateMatchScreen(
    groupId: String,
    matchState: com.example.sidequest.ui.match.MatchState,
    onBackClick: () -> Unit,
    onCreateMatchClick: (Int) -> Unit,
    onMatchCreated: (String) -> Unit
) {
    var challengeCount by remember { mutableStateOf("3") }
    val context = LocalContext.current

    LaunchedEffect(matchState.matchCreatedId) {
        matchState.matchCreatedId?.let { onMatchCreated(it) }
    }

    LaunchedEffect(matchState.error) {
        matchState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "New Match Configuration", style = MaterialTheme.typography.headlineLarge)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = challengeCount,
                onValueChange = { if (it.all { char -> char.isDigit() }) challengeCount = it },
                label = { Text("Challenges per Player") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !matchState.isCreating
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { onCreateMatchClick(challengeCount.toIntOrNull() ?: 3) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !matchState.isCreating && challengeCount.isNotBlank()
            ) {
                Text("Launch Match")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !matchState.isCreating
            ) {
                Text("Cancel")
            }
        }

        if (matchState.isCreating) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun ActiveMatchScreen(
    matchId: String,
    matchState: com.example.sidequest.ui.match.MatchState,
    onBackClick: () -> Unit,
    onViewChallengesClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Active Match", style = MaterialTheme.typography.headlineLarge)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val match = matchState.activeMatch
            if (match != null) {
                Text(text = "Match ID: ${match.id}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Status: ${match.status}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Challenges per Player: ${match.challengeCountPerPlayer}", style = MaterialTheme.typography.bodyMedium)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(onClick = onViewChallengesClick, modifier = Modifier.fillMaxWidth()) {
                    Text("View My Challenges")
                }
            } else {
                Text(text = "Loading match data...", style = MaterialTheme.typography.bodyMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Group")
            }
        }
    }
}

@Composable
fun MyChallengesScreen(
    matchState: com.example.sidequest.ui.match.MatchState,
    onBackClick: () -> Unit,
    onChallengeClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "My Challenges", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { innerPadding ->
        if (matchState.userChallenges.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No challenges assigned yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(matchState.userChallenges) { item ->
                    ChallengeCard(
                        challenge = item,
                        onClick = { onChallengeClick(item.assignment.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChallengeCard(
    challenge: ChallengeWithAssignment,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = challenge.template.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "${challenge.template.points} pts",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = challenge.template.title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status: ${challenge.assignment.status.name}",
                style = MaterialTheme.typography.bodySmall,
                color = if (challenge.assignment.status == com.example.sidequest.data.ChallengeStatus.COMPLETED) Color.Green else Color.Gray
            )
        }
    }
}

@Composable
fun ChallengeDetailsScreen(
    matchState: com.example.sidequest.ui.match.MatchState,
    onBackClick: () -> Unit,
    onSubmitProofClick: () -> Unit
) {
    val challenge = matchState.selectedChallenge

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Challenge Details", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { innerPadding ->
        if (challenge == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text(
                    text = challenge.template.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = challenge.template.difficulty.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${challenge.template.points} Points",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = challenge.template.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = onSubmitProofClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as Completed")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LandingPagePreview() {
    SideQuestTheme {
        LandingPage(onLoginClick = {}, onRegisterClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPagePreview() {
    SideQuestTheme {
        LoginPage(
            authState = com.example.sidequest.ui.auth.AuthState(),
            onLoginClick = { _, _ -> },
            onGoogleSignInClick = {},
            onRegisterClick = {},
            onErrorDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterPagePreview() {
    SideQuestTheme {
        RegisterPage(
            authState = com.example.sidequest.ui.auth.AuthState(),
            onRegisterClick = { _, _, _ -> },
            onLoginClick = {},
            onErrorDismiss = {}
        )
    }
}
