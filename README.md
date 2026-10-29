# Battery Saver (Android, Java)

Course project: a battery management app built with Java, AndroidX and Material 3.

## Open & run
1. Open Android Studio -> **File > Open** -> select the `BatterySaver` folder.
2. Let Gradle sync (Android Studio creates the Gradle wrapper automatically).
3. Run on an emulator or device (minSdk 24, targetSdk 34).

## Structure
```
BatterySaver/
├── build.gradle, settings.gradle, gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/batterysaver/
        │   ├── MainActivity.java      dashboard, chips, bottom nav, options menu + submenu
        │   ├── HistoryActivity.java   ListView, context menu, sort submenu
        │   ├── SettingsActivity.java  Slider + Switch, SharedPreferences
        │   └── BatteryLog.java        in-memory battery history
        └── res/
            ├── layout/  activity_main, activity_settings, activity_history
            ├── menu/    main_menu, history_menu, bottom_nav, context_menu
            ├── values/  strings, colors, themes
            ├── values-night/themes.xml
            └── drawable/ic_launcher.xml
```

## Rubric coverage
| Requirement | Where |
|---|---|
| Options Menu | MainActivity (`main_menu.xml`), HistoryActivity (`history_menu.xml`) |
| Submenu | Theme (main), Sort by (history) |
| Context Menu | HistoryActivity long-press on an entry |
| Navigation Bar | BottomNavigationView in MainActivity |
| Intents | Explicit (Activities), implicit (Display settings, Battery usage, Share) |
| Event handling | ChipGroup, Slider, Switch, buttons, menu items |
| Animation | Animated progress, pulsing level while charging, activity transitions |
| Material / AndroidX | Material 3 theme, chips, slider, Snackbar, dialog, ConstraintLayout |
