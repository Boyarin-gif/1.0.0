# Сборка Plus Visuals 1.0.0

## Требования

- JDK 21
- Gradle 9.2.0
- Доступ к Fabric Maven, Maven Central и Gradle Plugin Portal

В архиве нет Gradle Wrapper. Откройте терминал в корне проекта и выполните:

```bash
gradle build --no-daemon
```

Итоговый мод ожидается в `build/libs/plus-visual-1.0.0.jar`. Gradle также создаст `-sources.jar`.

## Сборка через GitHub Actions

В `.github/workflows/build.yml` настроен workflow: после `push` или ручного запуска GitHub Actions устанавливает Java 21 и Gradle 9.2.0, выполняет `gradle build --no-daemon` и публикует артефакт `pvpplus-jar`.

## Проверка

Команда сборки в данной рабочей среде не была успешно выполнена: Gradle и Wrapper отсутствуют, а загрузка Gradle дистрибутива была недоступна по сети. Не считайте этот исходный архив уже собранным или готовым к установке JAR. Перед установкой проверьте завершение GitHub Actions без ошибок, затем протестируйте запуск Minecraft 1.21.11 с Fabric API и Sodium.
