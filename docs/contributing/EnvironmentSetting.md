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
