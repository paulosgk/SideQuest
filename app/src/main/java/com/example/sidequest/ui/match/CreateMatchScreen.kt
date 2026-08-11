package com.example.sidequest.ui.match

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CreateMatchScreen(
    groupId: String,
    matchState: MatchState,
    onBackClick: () -> Unit,
    onCreateMatchClick: (Int) -> Unit,
    onMatchCreated: (String) -> Unit
) {
    var challengeCount by remember { mutableStateOf("5") }
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
                onClick = { onCreateMatchClick(challengeCount.toIntOrNull() ?: 5) },
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
