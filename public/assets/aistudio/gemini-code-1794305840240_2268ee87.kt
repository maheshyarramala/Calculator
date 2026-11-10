if (uiState.showBackupModal) {
  BackupRestoreBottomSheet(
    categoriesCount = categories.size,
    banksCount = bankAccounts.size,
    tripsCount = trips.size,
    expensesCount = expenses.size,
    loansCount = allLoans.size,
    debtsCount = allDebts.size,
    subscriptionsCount = subscriptions.size,
    cardRemindersCount = creditCardBillReminders.size,
    replaceExisting = uiState.replaceExistingOnImport,
    onReplaceExistingChange = { viewModel.setReplaceExistingOnImport(it) },
    onExportToFile = {
      val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
      exportFileLauncher.launch("OmniCalc_Backup_$dateStr.json")
    },
    onShareBackup = { viewModel.shareBackup(context) },
    onCopyJson = { viewModel.copyBackupJson(context) },
    onImportFromFile = {
      importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    },
    onPasteJsonClick = {
      viewModel.setShowBackupModal(false)
      viewModel.setShowPasteJsonDialog(true)
    },
    onDismiss = { viewModel.setShowBackupModal(false) }
  )
}