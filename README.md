# Phaze Client — Minecraft 1.21.11

Перенос клиента из `D:\projects\Phaze Client` с сохранением API рендера 1.21.11.
Java 21; версии Minecraft, Yarn и Fabric задаются в `gradle.properties`.
Старые скомпилированные миксины из 1.21.4 в JAR не накладываются.

## Проверки

```powershell
.\gradlew.bat build --no-daemon
.\gradlew.bat verifyMixinTargets --no-daemon
.\gradlew.bat runPortSmoke --no-daemon
```

`build` проверяет подпись и соответствие каталога модулей, ресурсы,
CPU-регрессии и ссылки зарегистрированных миксинов на байткод 1.21.11.
`runPortSmoke` запускает настоящий клиент: меню, тестовый одиночный мир,
инвентарь, третье лицо, косметику и перезагрузку ресурсов.
Мир, конфиги и снимки этого запуска находятся в `build/port-smoke-client`,
а `APPDATA`/`user.home` изолированы от обычной установки игрока.

Для офлайн-проверки необязательных миксинов можно передать папку JAR:

```powershell
.\gradlew.bat verifyMixinTargets -PportOptionalClasspath="D:/path/to/compat-jars"
```

Отчёт: `build/reports/port/mixin-targets.txt`. Отсутствующие необязательные
моды перечисляются отдельно: их совместимость не считается проверенной.
`-PportCompatMods="D:/path/to/compat-jars"` подключает моды к тестовому запуску,
не упаковывая их в Phaze. Версии этих JAR должны поддерживать используемые
Fabric Loader и Loom.

Успешная сборка/проверка байткода не доказывает визуальное совпадение или рост FPS.
Нужны отдельные проверки с Sodium/Iris, shaderpack, Exordium/ImmediatelyFast,
разными GUI Scale, фонами HUD и большими координатами. Сравнивайте frametime
в одинаковой сцене с выключенными Fake FPS и VSync.
