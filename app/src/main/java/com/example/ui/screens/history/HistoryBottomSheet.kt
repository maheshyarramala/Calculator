package com.example.ui.screens.history
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CalculationRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
  viewModel: HistoryViewModel,
  onDismiss: () -> Unit
) {
  val historyList by viewModel.historyList.collectAsState()
  val selectedFilter by viewModel.selectedFilter.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var showClearConfirm by remember { mutableStateOf(false) }

  val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

  val filters = listOf(
    "ALL" to "All",
    "EMI" to "Loans & EMI",
    "SIP" to "SIP",
    "LUMPSUM" to "Lump Sum",
    "CALCULATOR" to "Calculator",
    "AGE" to "Age",
    "CALENDAR" to "Calendar",
    "CURRENCY" to "Currency",
    "TIP" to "Tip & Split",
    "GST" to "GST"
  )

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text("Clear All History?") },
      text = { Text("This will permanently remove all your saved calculations.") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearAll()
            showClearConfirm = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Clear All")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    modifier = Modifier.testTag("history_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Saved Calculations",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "${historyList.size} records saved locally",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (historyList.isNotEmpty()) {
          TextButton(
            onClick = { showClearConfirm = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear", fontSize = 12.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Filter Chips
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(filters) { (key, label) ->
          FilterChip(
            selected = selectedFilter == key,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.setFilter(key)
            },
            label = { Text(label, fontSize = 12.sp) }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (historyList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Default.BookmarkBorder,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No saved calculations yet",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Use the bookmark icon on any screen to save!",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxHeight(0.7f)
        ) {
          items(historyList, key = { it.id }) { item ->
            HistoryItemCard(
              item = item,
              dateFormat = dateFormat,
              onToggleFavorite = { viewModel.toggleFavorite(item) },
              onShare = { viewModel.shareRecord(context, item) },
              onDelete = { viewModel.delete(item) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryItemCard(
  item: CalculationRecord,
  dateFormat: SimpleDateFormat,
  onToggleFavorite: () -> Unit,
  onShare: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        SuggestionChip(
          onClick = {},
          label = { Text(item.category, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
          ),
          border = null,
          modifier = Modifier.height(24.dp)
        )
        Text(
          text = dateFormat.format(Date(item.timestamp)),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = item.title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )

      Text(
        text = item.summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
