# Требования к окружению

Разработка ведется с использованием [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/).  

## Необходимое ПО

* Java Development Kit 17 или новее (до JDK 25)
* [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)
* Плагины IntelliJ IDEA
    * Lombok Plugin
    * EditorConfig Plugin

Обратите внимание, что плагины необязательно устанавливать - при наличии доступа в интернет, они будут установлены автоматически при импорте проекта.

### Настройки IntelliJ IDEA

* Настроить [Java SDK на JDK17 или новее](https://www.jetbrains.com/help/idea/sdk.html#manage_sdks)
* Включить обработку аннотаций: `File -> Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable annotation processing`
* Выполнить настройки автоимпорта, подробно описано в [статье](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html). Отдельно стоит обратить внимание на оптимизацию импорта.
    * Не надо запускать оптимизацию импортов всего проекта, за этим следят мейнтейнеры. Если после оптимизации импортов появились измененные файлы, которые не менялись в процессе разработки, стоит уведомить мейнтейнеров и откатить эти изменения.

## Локаль и кодировка

Проект двуязычный, и среди тестовых фикстур есть файлы с кириллическими именами (например, `Документ1.xml`). JVM декодирует имена файлов по системной локали (`sun.jnu.encoding`): при `LC_CTYPE=POSIX`/`C` берётся ASCII, и такие фикстуры не читаются — падают `processTestResources` и часть тестов. Запускайте сборку в UTF-8-локали:

* Linux/macOS: `LANG=C.UTF-8 ./gradlew …` (или задайте UTF-8-локаль в окружении);
* Windows: используйте UTF-8 кодовую страницу консоли.
