# TikTok Repost Cleaner

Android-приложение для автоматического удаления репостов в TikTok через Android Accessibility Service.

## Требования
- Android 8.0+ (API 26+)
- TikTok установлен (com.zhiliaoapp.musically или com.ss.android.ugc.trill)
- Android Studio Hedgehog или новее
- JDK 17

## Сборка
1. Открыть проект в Android Studio
2. Sync Gradle
3. Run app на устройстве (эмулятор не поддерживает Accessibility Service полноценно)

## Архитектура

```
app/
├── accessibility/
│   └── TikTokAccessibilityService.kt   # Ядро: взаимодействие с TikTok UI
├── data/
│   ├── database/AppDatabase.kt         # Room DB: хранение репостов и сессий
│   └── repository/RepostRepository.kt  # Repository паттерн
├── di/AppModule.kt                     # Hilt DI
├── domain/
│   ├── model/Models.kt                 # Доменные модели
│   └── usecase/UseCases.kt             # Use Cases
└── presentation/
    ├── components/Components.kt        # Переиспользуемые Compose компоненты
    ├── screens/MainScreen.kt           # Все экраны приложения
    ├── theme/Theme.kt                  # TikTok-стиль дизайн
    └── viewmodel/MainViewModel.kt      # MVVM ViewModel
```

## Как это работает

1. **Accessibility Service** (`TikTokAccessibilityService`) регистрируется для пакетов TikTok
2. При запуске сканирования сервис:
   - Навигирует в профиль пользователя через интерфейс TikTok
   - Находит вкладку репостов
   - Прокручивает список, собирая ID видео
   - Возвращает события через `SharedFlow` в ViewModel
3. При удалении сервис:
   - Выполняет long press на каждое видео
   - Находит пункт "Remove repost" в контекстном меню
   - Подтверждает действие
4. Весь прогресс сохраняется в Room Database

## Разрешения
- `BIND_ACCESSIBILITY_SERVICE` — основное разрешение, даётся пользователем вручную
- `INTERNET` — не используется для передачи данных, только для работы TikTok
- `FOREGROUND_SERVICE` — зарезервировано для фонового режима

## Ограничения
- Интерфейс TikTok может меняться — при обновлении приложения может потребоваться адаптация
- Apple/China-версии TikTok (Douyin) не поддерживаются
- Требует активный экран во время работы
