# Performance measurement

The app has a built-in module for collecting performance metrics.

By default, performance measurement is disabled. To enable measurements, you can use one of the following methods:

* specify in `application.properties` the setting `app.measures.enabled = true`;
* specify `--app.measures.enabled = true` parameter as command line argument when starting `java -jar` or `exe`;
* set environment variable `APP_MEASURES_ENABLED` with value `true`.

The output of the performance measurement occurs after the `analyze` command finishes.

## Implementation

Measurement logic implemented in aspect `com.github._1c_syntax.bsl.languageserver.aop.MeasuresAspect` and package `com.github._1c_syntax.bsl.languageserver.aop.measures`.

Aspect `MeasuresAspect` intercepts and measures calls to `ServerContext`, various computers, and calls to calculate diagnostics.

`com.github._1c_syntax.bsl.languageserver.aop.measures.DocumentContextLazyDataMeasurer` handles the `DocumentContext` rebuild event and pre-calculates the cached data (parse tree, metrics, symbol tree, etc.) with measurement of each component.

## Example

Fragment of measurement results:

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
