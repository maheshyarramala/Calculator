@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreBottomSheet(
  categoriesCount: Int,
  banksCount: Int,
  tripsCount: Int,
  expensesCount: Int,
  loansCount: Int,
  debtsCount: Int,
  subscriptionsCount: Int,
  cardRemindersCount: Int,
  replaceExisting: Boolean,
  onReplaceExistingChange: (Boolean) -> Unit,
  onExportToFile: () -> Unit,
  onShareBackup: () -> Unit,
  onCopyJson: () -> Unit,
  onImportFromFile: () -> Unit,
  onPasteJsonClick: () -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 36.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text("Data Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Export & import offline JSON files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // Export Section
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Data (Backup)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
          Text(
            text = "Choose a destination folder (Downloads, Drive, SD Card) to save your offline JSON backup.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = onExportToFile,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Backup to Device (Choose Folder)")
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = onShareBackup,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share File", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
              onClick = onCopyJson,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Copy JSON", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
          }
        }
      }

      // Import Section
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF059669))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Data (Restore)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
          Text(
            text = "Browse your phone storage to select an OmniCalc JSON file to restore.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = !replaceExisting,
              onClick = { onReplaceExistingChange(false) },
              label = { Text("Merge Data") },
              modifier = Modifier.weight(1f)
            )
            FilterChip(
              selected = replaceExisting,
              onClick = { onReplaceExistingChange(true) },
              label = { Text("Overwrite (Replace)") },
              modifier = Modifier.weight(1f)
            )
          }

          Button(
            onClick = onImportFromFile,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Backup File from Device")
          }

          OutlinedButton(
            onClick = onPasteJsonClick,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Paste JSON Text Directly", fontSize = 12.sp)
          }
        }
      }
    }
  }
}