package com.shadowai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ProviderUsage and UsageCreditsViewModel moved to separate files

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageCreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageCreditsViewModel = hiltViewModel()
) {
    val usageData by viewModel.usageData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage & Credits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier.systemBarsPadding()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Text(
                    text = "Credit Usage Summary",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "This is a read-only display of your credit consumption per provider. Credits are never shown in the chat UI or provider selectors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            items(usageData) { usage ->
                ProviderUsageCard(usage = usage)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ProviderUsageCard(usage: ProviderUsage) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = usage.providerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val creditValue = if (usage.isReported) usage.reportedCredits else usage.estimatedCredits
            val label = if (usage.isReported) "Reported (API)" else "Estimated (local)"
            val color = if (usage.isReported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Credits Used:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "%.2f".format(creditValue),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
