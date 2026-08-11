package com.example.sidequest.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActiveMatchScreen(
    matchId: String,
    matchState: MatchState,
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
