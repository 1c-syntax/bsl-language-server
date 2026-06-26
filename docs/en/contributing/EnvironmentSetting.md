# Environment requirements

Development is underway using [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/).

## Required Software

* Java Development Kit 17 or newer (up to JDK 25)
* [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)
* Plugins IntelliJ IDEA
    * Lombok Plugin
    * EditorConfig Plugin

Please note that plugins do not have to be installed - if you have Internet access, they will be installed automatically when you import the project.

### IntelliJ IDEA Settings

* Set [Java SDK to Jdk17 or newer](https://www.jetbrains.com/help/idea/sdk.html#manage_sdks)
* Enable annotation processing: `File -> Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable annotation processing`
* Configure auto import settings, details in the [article](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html). Pay special attention to import optimization.
    * There is no need to start optimization of imports of the entire project, this is followed by maintainers. If, after optimizing imports, changed files appeared that did not change during the development process, you should notify the maintainers and roll back these changes.

## Locale and encoding

The project is bilingual, and test fixtures include files with Cyrillic names (e.g. `Документ1.xml`). The JVM decodes file names using the system locale (`sun.jnu.encoding`): under `LC_CTYPE=POSIX`/`C` it falls back to ASCII and such fixtures cannot be read, breaking `processTestResources` and some tests. Run the build in a UTF-8 locale:

* Linux/macOS: `LANG=C.UTF-8 ./gradlew …` (or set a UTF-8 locale in your environment);
* Windows: use a UTF-8 console code page.
