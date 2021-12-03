# Замер производительности

В приложение встроен модуль сбора показателей производительности.

По умолчанию замер производительности выключен. Для включения замеров можно воспользоваться одним из трех способов:

* указать в `application.properties` настройку `app.measures.enabled=true`;
* передать в качестве аргумента командной строки при запуске `java -jar` или `exe` параметр `--app.measures.enabled=true`;
* задать переменную среды `APP_MEASURES_ENABLED` со значением `true`. 

Вывод результата замера производительности происходит после окончания работы команды `analyze`.

## Реализация

Основная логика замеров реализована в аспекте `com.github._1c_syntax.bsl.languageserver.aop.MeasuresAspect` и пакете `com.github._1c_syntax.bsl.languageserver.aop.measures`.

Аспект `MeasuresAspect` перехватывает и замеряет вызовы `ServerContext`, различных компьютеров и вызова расчета диагностик.

`com.github._1c_syntax.bsl.languageserver.aop.measures.DocumentContextLazyDataMeasurer` обрабатывает событие перестроения `DocumentContext` и выполняет предрасчет кэшируемых данных (дерево разбора, метрики, дерево символов и т.д.) c замером каждого компонента.

## Пример

Фрагмент результатов замера:

```log
c.g._.b.l.aop.measures.MeasureCollector  : diagnostic: NestedStatements - 4139
c.g._.b.l.aop.measures.MeasureCollector  : diagnostic: UnreachableCode - 4555
c.g._.b.l.aop.measures.MeasureCollector  : computer: DiagnosticIgnoranceComputer - 4704
c.g._.b.l.aop.measures.MeasureCollector  : context: diagnosticIgnorance - 4750
c.g._.b.l.aop.measures.MeasureCollector  : diagnostic: ParseError - 6383
c.g._.b.l.aop.measures.MeasureCollector  : diagnostic: DeprecatedMethodCall - 6828
c.g._.b.l.aop.measures.MeasureCollector  : diagnostic: LatinAndCyrillicSymbolInWord - 8164
c.g._.b.l.aop.measures.MeasureCollector  : computer: QueryComputer - 8680
c.g._.b.l.aop.measures.MeasureCollector  : computer: MethodSymbolComputer - 8829
c.g._.b.l.aop.measures.MeasureCollector  : context: queries - 8933
c.g._.b.l.aop.measures.MeasureCollector  : context: metrics - 9864
c.g._.b.l.aop.measures.MeasureCollector  : diagnostic: UsingHardcodeNetworkAddress - 11995
```
