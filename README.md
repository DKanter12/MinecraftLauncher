# MDD Launcher

Десктопный лаунчер Minecraft (JavaFX): сборки Vanilla / Fabric / Forge / NeoForge / Quilt,
импорт `.mrpack`, аккаунты offline и Ely.by, установка Java, проверка целостности,
самообновление, RU/EN интерфейс, темы оформления.

## Требования

- **JDK 17** (например, `C:\Program Files\Java\jdk-17`).
  Gradle 8.5 не работает на более новых JDK — переменную `JAVA_HOME` нужно указывать явно.
- Windows (для `desktopPackage` / jpackage).

## Запуск как приложение

Собрать standalone-приложение `MDD.exe` (app-image, с иконкой, без консоли):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat desktopPackage
```

Готовое приложение: `build\dist\MDD\MDD.exe` — можно запускать двойным
кликом или ярлыком с рабочего стола. Папку `build\dist\MDD` можно копировать
на другие машины целиком.

## Темы интерфейса

В настройках лаунчера:

- **Стандартная** — тёмно-зелёная тема по умолчанию.
- **Своя** — строится из двух цветов: **фона** и **акцента** (пикеры
  цвета). Остальные ~40 оттенков палитры (поверхности, границы, тексты,
  варианты акцента) вычисляются автоматически: тёмный фон даёт светлые
  поверхности, светлый — тёмные; текст всегда контрастен фону. Под
  превью-полоской видно, как будет выглядеть тема.
- Применяется сразу ко всем окнам, выбор сохраняется (в
  `launcher_preferences.json`: `theme`, `customThemeBg`,
  `customThemeAccent`).

## Выпуск обновления

1. Поднимите `version` в `launcher-version.json`.
2. Соберите пакет:

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   .\gradlew.bat launcherUpdatePackage
   ```

   Готовый ZIP: `build\updates\launcher-<version>.zip`; задача печатает
   его SHA-256 и размер.
3. Скопируйте ZIP в `updates\launcher-<version>.zip`.
4. В `launcher-version.json` укажите URL вида
   `https://raw.githubusercontent.com/DKanter12/MinecraftLauncher/refs/heads/main/updates/launcher-<version>.zip`,
   а также `package.sha256` и `package.size` из вывода задачи.
5. Закоммитьте и запушьте `launcher-version.json` и ZIP.

Для raw-ссылок на новые ZIP используйте форму `refs/heads/main`:
короткий путь `.../main/...` для только что загруженных больших файлов
может временно отдавать 404.

## Запуск в режиме разработки

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat run
```

## Тесты

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat test
```

## Где хранятся данные

Всё в `~/.minecraft`:

- `instances.json`, `launcher_profiles.json`, `launcher_preferences.json`, `server_session.json`;
- сборки: `profiles/<loader>-<mc>/<name>/`;
- общие каталоги: `versions/`, `libraries/`, `assets/`, `natives/`, `java-runtimes/`.
