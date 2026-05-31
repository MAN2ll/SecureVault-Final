@Composable
fun EntryCard(entry: Entry, onClick: () -> Unit) {
    //  Цвет карточки в зависимости от статуса пароля
    val borderColor = when (entry.getExpiryStatus()) {
        Entry.ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.error
        Entry.ExpiryStatus.CRITICAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        Entry.ExpiryStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        Entry.ExpiryStatus.OK -> MaterialTheme.colorScheme.outline
    }
    
    val daysUntilExpiry = entry.getDaysUntilExpiry()
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.service,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = { /* Копировать */ }) {
                    Icon(Icons.Default.ContentCopy, "Копировать")
                }
            }
            
            // 🔖 Метка профиля + статус пароля
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (entry.profile == Profile.WORK) " Работа" else " Личное",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                //  Индикатор срока действия
                if (entry.getExpiryStatus() != Entry.ExpiryStatus.OK) {
                    val statusText = when (entry.getExpiryStatus()) {
                        Entry.ExpiryStatus.EXPIRED -> " Просрочен"
                        Entry.ExpiryStatus.CRITICAL -> " $daysUntilExpiry д."
                        Entry.ExpiryStatus.WARNING -> " $daysUntilExpiry д."
                        Entry.ExpiryStatus.OK -> ""
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = borderColor
                    )
                }
            }
        }
    }
}
