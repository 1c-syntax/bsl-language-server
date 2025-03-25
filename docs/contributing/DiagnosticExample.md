# Пример добавления своей диагностики

Диагностика - это класс, выполняющий анализ исходного кода для выявления проблемы или ошибки.

## Содержимое диагностики (кратко)

В общем виде, для реализации диагностики необходимо создать несколько файлов

* Класс, реализующий диагностику
* Два файла ресурсов (для английского и русского языка), содержащих название диагностики и сообщение об ошибке
* Файл фикстуры, содержащий BSL код, содержимое которого используется для тестирования диагностики
* Класс теста для диагностики
* Два файла (для английского и русского языка) с описанием диагностики, алгоритма ее работы и примеров и т.д.

Подробное описание содержимого файлов и правил использования находится в [статье](DiagnosticStructure.md).

## Создание диагностики

Ниже рассмотрен пример создания диагностики по мотивам уже созданной.

### Определение назначения диагностики

Перед реализацией новой диагностики, необходимо определить ее цель - какую ошибку (или недочет) необходимо обнаружить. 
В качестве примера напишем диагностику, проверяющую наличие точки с запятой `;` в конце каждого выражения.  
После определения цели, необходимо придумать уникальный ключ диагностики, под которым она будет добавлена в общий список.
Для создаваемой диагностики возьмем имя `SemicolonPresence`.

### Класс реализации диагностики

В соответствии с правилами, каталоге `src/main/java` в пакете `com.github._1c_syntax.bsl.languageserver.diagnostics` создадим файл `SemicolonPresenceDiagnostic.java` класса диагностики.  
В файле создаем одноименный класс, унаследованный от класса `AbstractVisitorDiagnostic`. В результате имеем следующее

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic {
}
```

Каждая диагностика должна иметь аннотацию класса `@DiagnosticMetadata`, содержащую метаданные диагностики. Подробная информация об аннотациях в [статье][DiagnosticStructure].  
В примере у нас реализуется диагностика, относящаяся к качеству кода (`CODE_SMELL`), низкого приоритета (`MINOR`), требующая для исправления 1 минуту и относящаяся к стандарту 1С. Итоговый вид класса с аннотацией

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tag = {
    DiagnosticTag.STANDARD
  }
)
public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic {
}
```

### Ресурсы класса

В соответствии с правилами, в каталоге `src/main/resources` в пакете `com.github._1c_syntax.bsl.languageserver.diagnostics` создаем 2 файла ресурсов диагностики, в нашем примере это будут файлы `SemicolonPresenceDiagnostic_ru.properties` и `SemicolonPresenceDiagnostic_en.properties`.  
В созданных файлах сохраним название диагностики (параметр `diagnosticName`) и сообщение замечания (`diagnosticMessage`).  
В нашем примере содержимое файлов будет следующим

Файл `SemicolonPresenceDiagnostic_ru.properties`

```properties
diagnosticMessage=Пропущена точка с запятой в конце выражения
diagnosticName=Выражение должно заканчиваться ";"
```

Файл `SemicolonPresenceDiagnostic_en.properties`

```properties
diagnosticMessage=Missed semicolon at the end of statement
diagnosticName=Statement should end with ";"
```

### Фикстуры для теста

Для тестирования добавим в проект файл, содержащий примеры как ошибочного, так и корректного кода. Файл `SemicolonPresenceDiagnostic.bsl` с фикстурами разместим в каталоге `src/test/resources` в пакете `diagnostics`.  
В качестве данных для тестирования, внесем в файл следующий код

```bsl
А = 0;
Если Истина Тогда
  А = 0;
  А = 0           // Диагностика должна сработать здесь
КонецЕсли         // и здесь
```

**Внимание!**
Стоит принять за правило: помечать комментариями места, где диагностика должна сработать.

### Написание теста

В каталоге `src/test/java` в пакете `com.github._1c_syntax.bsl.languageserver.diagnostics` создаем файл `SemicolonPresenceDiagnosticTest.java` для тестового класса диагностики.  
В файле необходимо создаем одноименный класс, унаследованый от класса `AbstractDiagnosticTest` для созданного класса диагностики.  
В результате имеем следующее

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

class SemicolonPresenceDiagnosticTest extends AbstractDiagnosticTest<SemicolonPresenceDiagnostic>{
    SemicolonPresenceDiagnosticTest() {
        super(SemicolonPresenceDiagnostic.class);
    }
}
```

Упрощенный базовый тест состоит из следующих шагов

* получение списка диагностик
* проверка количества срабатываний
* проверка местоположения срабатываний

Не останавливаясь подробно, запоминаем, что

* Для получения диагностик по фикстуре используется метод `getDiagnostics()` реализованный в абстрактном классе `AbstractDiagnosticTest`. Он возвращает список сработавших диагностик текущего типа.
* Для проверки количество срабатываний необходимо проверить размер массива утверждением `hasSize`, куда передать количество ожидаемых элементов.
* Для проверки каждого обнаруженного элемента, необходимо сравнить найденный диапазон символов с ожидаемым.

Полученный класс теста выглядит следующим образом

```java
class SemicolonPresenceDiagnosticTest extends AbstractDiagnosticTest<SemicolonPresenceDiagnostic> {

  SemicolonPresenceDiagnosticTest() {
    super(SemicolonPresenceDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics(); // Получение диагностик

    assertThat(diagnostics).hasSize(2); // Проверка количества
    assertThat(diagnostics, true)
      .hasRange(4, 0, 4, 9)  // Проверка конкретного случая
      .hasRange(3, 6, 3, 7); // Проверка конкретного случая
  }
}
```

### Реализация диагностики

В соответствии грамматикой языка, описанной в проекте [BSLParser](https://github.com/1c-syntax/bsl-parser/blob/master/src/main/antlr/BSLParser.g4), для нашего примера необходимо анализировать узлы с типом `statement`, следовательно нужно использовать визитер `visitStatement`. Каждый выбранный узел должен содержать конечную "точку с запятой", представленную узлом `SEMICOLON`.

Таким образом, проверка будет состоять в том, чтобы в каждом узле `statement` найти токен `SEMICOLON`. Если токен не будет найден, то необходимо зарегистрировать замечание.  
После реализации проверки, файл примет следующий вид

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tag = {
    DiagnosticTag.STANDARD
  }
)
public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic {
    @Override
    public ParseTree visitStatement(BSLParser.StatementContext ctx) { // выбранный визитер
        if (ctx.SEMICOLON() == null) {                                // получение дочернего узла SEMICOLON
            diagnosticStorage.addDiagnostic(ctx);                     // добавление замечания
        }
        // Для не-терминальных выражений в качестве возвращаемого значения
        // обязательно должен вызываться super-метод.
        return super.visitStatement(ctx);
    }
}
```

Необходимо запустить тест диагностики и убедиться в корректной работе.

### Создание описания диагностики

Для пользовательского описания созданной диагностики создаем два файла `SemicolonPresence.md`: в каталоге `docs/diagnostics` на русском языке, в каталоге `docs/en/diagnostics` на английском языке, и описываем диагностику.  

## Завершение

Обновление индекса справки выполняется автоматически при сборке сайта документации, поэтому ничего руками делать не стоит.  

Чтобы не забыть все действия, которые необходимо выполнить перед окончанием разработки, нужно выполнить `gradlew precommit` из командной строки либо `precommit` из панели задач Gradle в IDE. 
