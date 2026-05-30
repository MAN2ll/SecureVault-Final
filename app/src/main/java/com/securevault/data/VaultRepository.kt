// Добавь этот метод в класс VaultRepository:
suspend fun createEntry(
    service: String,
    username: String,
    password: String,
    category: String = "general",
    profile: Profile = Profile.PERSONAL,
    notes: String = "",
    changeIntervalDays: Int = 90
) {
    val entry = Entry.create(
        service = service,
        username = username,
        password = password, // автоматически зашифруется внутри Entry.create()
        category = category,
        profile = profile,
        notes = notes,
        changeIntervalDays = changeIntervalDays
    )
    insert(entry)
}
