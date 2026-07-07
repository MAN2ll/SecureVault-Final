// Диалог истории паролей
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordHistoryDialog(
    entry: Entry,
    onDismiss: () -> Unit
) {
    val history = entry.getPasswordHistory()
    var revealedIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showMasterPassword by remember { mutableStateOf(false) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История паролей", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Предыдущие пароли записи. Для просмотра введите мастер-пароль.", fontSize = 12.sp)
                history.forEachIndexed { index, item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Предыдущий пароль #${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "••••••••",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    pendingIndex = index
                                    showMasterPassword = true
                                }) {
                                    Text("Показать")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )

    if (showMasterPassword) {
        MasterPasswordConfirmDialog(
            title = "Показать старый пароль",
            onConfirmed = {
                pendingIndex?.let { idx ->
                    revealedIndexes = revealedIndexes + idx
                }
                showMasterPassword = false
                pendingIndex = null
            },
            onDismiss = {
                showMasterPassword = false
                pendingIndex = null
            }
        )
    }
}
