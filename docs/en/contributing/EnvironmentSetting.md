# Требования к окружению

Development is underway using [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/).

## Required Software

* Java Development Kit 11
* [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)
* Plugins IntelliJ IDEA
    * Lombok Plugin
    * EditorConfig Plugin

Please note that plugins do not have to be installed - if you have Internet access, they will be installed automatically when you import the project.

### IntelliJ IDEA Settings

* Configure [Java SDK на JDK11](https://www.jetbrains.com/help/idea/sdk.html#manage_sdks)
* Enable annotation processing: `File -> Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable annotation processing`
* Configure auto import settings, details in the [article](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html). Attention:
    * There is no need to start optimization of imports of the entire project, this is followed by maintainers. Если после оптимизации импортов появились измененные файлы, которые не менялись в процессе разработки, стоит уведомить мейнтейнеров и откатить эти изменения.
