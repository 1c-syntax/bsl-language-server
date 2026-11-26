# File system access (FileSystemAccess)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
It is important to review your code. Be sure to pay attention to accessing the file system and using “external code”

The found sections of the code must be analyzed, a manual audit of the code must be performed for its correctness and safety.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
    Текст = Новый ЧтениеТекста(ПутьФайла, КодировкаТекста.ANSI); // есть замечание
    Текст = Новый ЗаписьТекста(ПутьФайла, КодировкаТекста.ANSI); // есть замечание

    ЗначениеВФайл(ПутьФайла, ЛичныеДанные); // есть замечание
    КопироватьФайл(ПутьФайла, ДругойПутьФайла); // есть замечание

    МассивИмен = Новый Массив();
    МассивИмен.Добавить(ПутьФайла);
    ОбъединитьФайлы(МассивИмен, ДругойПутьФайла); // есть замечание

    ПереместитьФайл(ПутьФайла, ДругойПутьФайла); // есть замечание
    РазделитьФайл(ПутьФайла, 1024 * 1024 ); // есть замечание
    СоздатьКаталог(ИмяКаталога); // есть замечание
    УдалитьФайлы(ПутьФайла); // есть замечание
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* [File system access from application code (RU)](https://its.1c.ru/db/v8std#content:542:hdoc)
* [Standard: Application launch security (RU)](https://its.1c.ru/db/v8std#content:774:hdoc)
* [Safe operation - Developer's Guide (RU](https://its.1c.ru/db/v8323doc#bookmark:dev:TI000000186)
