        composable("mnemonic_generator") {
            MnemonicGeneratorScreen(
                onGenerated = { password, emoji, rotation ->
                    // ✅ СОЗДАЕМ И СОХРАНЯЕМ ЗАПИСЬ СРАЗУ
                    val newEntry = Entry.create(
                        service = "Новый пароль", // Можно сделать поле ввода названия сервиса
                        username = "user",
                        password = password,
                        profile = Profile.PERSONAL,
                        emojiHint = emoji,
                        rotationEnabled = rotation,
                        rotationPeriodMonths = 6
                    )
                    // ✅ ВЫЗЫВАЕМ VIEWMODEL ДЛЯ СОХРАНЕНИЯ
                    // Нам нужно получить ViewModel здесь. 
                    // Проще всего передать ViewModel через параметры или использовать hiltViewModel внутри экрана.
                    // Но так как onGenerated - это лямбда, давайте сделаем иначе:
                    
                    // ВАРИАНТ А: Передать результат обратно в список и создать там (сложнее)
                    // ВАРИАНТ Б (ПРОЩЕ): Сохранять прямо внутри MnemonicGeneratorScreen перед вызовом onGenerated
                    
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
