# Environment requirements

Development is underway using [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/).

## Required Software

* Java Development Kit 17
* [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)
* Plugins IntelliJ IDEA
    * Lombok Plugin
    * EditorConfig Plugin

Please note that plugins do not have to be installed - if you have Internet access, they will be installed automatically when you import the project.

### IntelliJ IDEA Settings

* Configure [Java SDK на JDK17](https://www.jetbrains.com/help/idea/sdk.html#manage_sdks)
* Enable annotation processing: `File -> Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable annotation processing`
* Configure auto import settings, details in the [article](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html). Pay special attention to import optimization.
    * There is no need to start optimization of imports of the entire project, this is followed by maintainers. If, after optimizing imports, changed files appeared that did not change during the development process, you should notify the maintainers and roll back these changes.
