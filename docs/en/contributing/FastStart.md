# Quick start

Ниже описана последовательность действий для быстрого старта разработки

## Создание новых диагностик

1. Создать каталог проекта `bsl-language-server`
2. Склонировать в созданный каталог репозиторий проекта `https://github.com/1c-syntax/bsl-language-server.git`
3. Выполнить настройку окружения по [инструкции](EnvironmentSetting.md)
4. Выполнить команды для ингнорирования изменений в служебных файлах
    1. `git update-index --assume-unchanged ./.idea/compiler.xml`
    2. `git update-index --assume-unchanged ./.idea/encodings.xml`
    3. `git update-index --assume-unchanged ./.idea/misc.xml`
5. Открыть файл `build.gradle.kts` из каталога проекта, согласиться с импортом зависимостей, дождаться их скачивания
6. Run (from context menu or ide console) command `gradlew test`, if passed then all settings are correct
7. Make yourself familiar with  [diagnostics development example](DiagnosticExample.md) , [structure and files purpose description,](DiagnosticStructure.md) and other articles in the [section for developers](index.md)

## Использование отладчика AST

Для анализа AST дерева при создании диагностик, может потребоваться получить визуальное представление дерева. Для этого необходимо выполнить следующие шаги

1. Создать каталог проекта `bsl-parser`
2. Склонировать в созданный каталог репозиторий проекта `https://github.com/1c-syntax/bsl-parser.git`
3. Set up the environment according to the [instruction](EnvironmentSetting.md) *(if not previously performed)*
4. Установить плагин `ANTLR v4 grammar plugin`
5. Открыть файл `build.gradle.kts` из каталога проекта, согласиться с импортом зависимостей, дождаться их скачивания
6. Открыть файл `src/main/antlr/BSLParser.g4`
7. Установить курсор на любую строку с кодом *(не комментарий)* и выбрать пункт контекстного меню `Test Rule file`
8. In the opened window, select a bsl-file or paste text from the clipboard
