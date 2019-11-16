# Quick start

The following describes the steps to get started quickly.

## Create new diagnostics

1. Create project directory `bsl-language-server`
2. Clone the project repository to the created directory `https://github.com/1c-syntax/bsl-language-server.git`
3. Set up the environment according to the [instructions](EnvironmentSetting.md)
4. Execute commands to ignore changes in service files
    1.  `git update-index --assume-unchanged ./.idea/compiler.xml` 
    2.  `git update-index --assume-unchanged ./.idea/encodings.xml` 
    3.  `git update-index --assume-unchanged ./.idea/misc.xml` 
5. Open the `build.gradle.kts` file from the project directory, agree to import the dependencies, wait for them to download
6. Run (from context menu or ide console) command `gradlew test`, if passed then all settings are correct
7. Make yourself familiar with  [diagnostics development example](DiagnosticExample.md) , [structure and files purpose description,](DiagnosticStructure.md) and other articles in the [section for developers](index.md)

## Using the AST Debugger

To analyze the AST tree when creating diagnostics, you may need to get a visual representation of the tree. To do this, follow these steps

1. Create `bsl-parser` project directory
2. Clone the project repository into the created directory `https://github.com/1c-syntax/bsl-parser.git`
3. Set up the environment according to the [instruction](EnvironmentSetting.md) *(if not previously performed)*
4. Install `ANTLR v4 grammar plugin`
5. Open the `build.gradle.kts` file from the project directory, agree to import the dependencies, wait for them to download
6. Open `src/main/antlr/BSLParser.g4`
7. Place the cursor on any line with a code *(not a comment)* and select the `Test Rule file` context menu item
8. In the opened window, select a bsl-file or paste text from the clipboard
