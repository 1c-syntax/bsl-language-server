# Поддержка LSIF (исследование)

## Что такое LSIF

LSIF (Language Server Index Format, произносится "else if") — это стандартный формат для хранения предвычисленных данных о коде, которые обычно предоставляются Language Server Protocol (LSP). LSIF позволяет сохранять результаты анализа кода в файл, который затем может использоваться для навигации по коду без запуска полноценного языкового сервера.

### Ключевые особенности

- **Предвычисленные данные**: LSIF сохраняет результаты анализа один раз (например, при сборке в CI), после чего они могут использоваться многократно
- **Не требует исходный код**: Для навигации по коду достаточно иметь LSIF-индекс
- **Графовая модель**: Данные представлены в виде графа с вершинами (vertices) и рёбрами (edges)
- **JSON-формат**: Используется NDJSON (Newline Delimited JSON) — каждая строка содержит один объект

### Сценарии использования

- Навигация по коду в веб-интерфейсах (GitHub, GitLab, Sourcegraph)
- Code review без запуска IDE
- Анализ больших кодовых баз
- Интеграция с системами документирования

## Спецификация LSIF 0.6.0

Официальная спецификация: https://microsoft.github.io/language-server-protocol/specifications/lsif/0.6.0/specification/

### Структура графа

#### Вершины (Vertices)

| Тип вершины | Описание |
|-------------|----------|
| `metaData` | Метаданные индекса (версия, инструмент и т.д.) |
| `project` | Проект |
| `document` | Документ (файл) |
| `range` | Диапазон в документе |
| `resultSet` | Набор результатов для символа |
| `hoverResult` | Результат hover (всплывающая подсказка) |
| `definitionResult` | Результат перехода к определению |
| `referenceResult` | Результат поиска использований |
| `foldingRangeResult` | Результат сворачиваемых областей |
| `documentSymbolResult` | Результат символов документа |
| `moniker` | Идентификатор для кросс-репозиторной навигации |

#### Рёбра (Edges)

| Тип ребра | Описание |
|-----------|----------|
| `contains` | Документ содержит диапазоны |
| `next` | Связь диапазона с набором результатов |
| `textDocument/hover` | Связь с результатом hover |
| `textDocument/definition` | Связь с результатом определения |
| `textDocument/references` | Связь с результатом использований |
| `textDocument/foldingRange` | Связь с результатом сворачивания |
| `textDocument/documentSymbol` | Связь с символами документа |
| `item` | Связь результата с конкретными диапазонами |

### Пример LSIF-файла

```json
{"id":1,"type":"vertex","label":"metaData","version":"0.6.0","projectRoot":"file:///project"}
{"id":2,"type":"vertex","label":"project","kind":"bsl"}
{"id":3,"type":"vertex","label":"document","uri":"file:///project/Module.bsl","languageId":"bsl"}
{"id":4,"type":"vertex","label":"range","start":{"line":0,"character":9},"end":{"line":0,"character":15}}
{"id":5,"type":"edge","label":"contains","outV":3,"inVs":[4]}
{"id":6,"type":"vertex","label":"hoverResult","result":{"contents":{"kind":"markdown","value":"**Процедура ТестоваяПроцедура()**"}}}
{"id":7,"type":"edge","label":"textDocument/hover","outV":4,"inV":6}
```

## Возможности BSL Language Server для экспорта в LSIF

### Поддерживаемые операции

| Операция | Провайдер | Статус поддержки |
|----------|-----------|------------------|
| `textDocument/hover` | `HoverProvider` | ✅ Полная поддержка |
| `textDocument/definition` | `DefinitionProvider` | ✅ Полная поддержка |
| `textDocument/references` | `ReferencesProvider` | ✅ Полная поддержка |
| `textDocument/documentSymbol` | `DocumentSymbolProvider` | ✅ Полная поддержка |
| `textDocument/foldingRange` | `FoldingRangeProvider` | ✅ Полная поддержка |
| `textDocument/documentLink` | `DocumentLinkProvider` | ✅ Полная поддержка |
| `textDocument/declaration` | — | ❌ Не применимо для BSL |
| `textDocument/typeDefinition` | — | ❌ Не применимо для BSL |
| `textDocument/implementation` | — | ❌ Не применимо для BSL |

### Существующая инфраструктура

BSL Language Server уже имеет всё необходимое для экспорта в LSIF:

1. **ReferenceIndex** — индекс ссылок, хранящий информацию о связях между символами
2. **SymbolTree** — дерево символов документа
3. **ServerContext** — контекст сервера с информацией обо всех документах проекта
4. **Провайдеры** — готовые реализации для получения данных hover, definition, references и т.д.

## Предлагаемая архитектура реализации

На основе анализа [lsif-java](https://github.com/microsoft/lsif-java) предлагается следующая архитектура.

### Новый CLI-модуль

По аналогии с существующими командами (`analyze`, `format`) и подходом lsif-java, предлагается добавить команду `lsif`:

```sh
java -jar bsl-language-server.jar lsif --srcDir ./src/cf --output ./dump.lsif
java -jar bsl-language-server.jar lsif --srcDir ./src/cf --output ./dump.lsif --format json
```

**Параметры:**
- `--srcDir` / `-s` — путь к каталогу исходных файлов
- `--output` / `-o` — путь к выходному файлу (по умолчанию: `dump.lsif`)
- `--format` — формат вывода: `line` (NDJSON, по умолчанию) или `json`

### Структура модулей (по аналогии с lsif-java)

```
src/main/java/com/github/_1c_syntax/bsl/languageserver/
├── cli/
│   └── LsifCommand.java              # CLI-команда для генерации LSIF
└── lsif/
    ├── LsifService.java              # Оркестратор индексации (аналог LsifService)
    ├── LsifIndexerContext.java       # Контекст индексации (аналог IndexerContext)
    ├── LsifEmitter.java              # Генератор LSIF-вывода (аналог Emitter)
    ├── protocol/                     # Модели LSIF-протокола
    │   ├── Element.java              # Базовый интерфейс (id, type)
    │   ├── Vertex.java               # Интерфейс вершины
    │   ├── Edge.java                 # Интерфейс ребра
    │   ├── vertex/
    │   │   ├── MetaDataVertex.java
    │   │   ├── ProjectVertex.java
    │   │   ├── DocumentVertex.java
    │   │   ├── RangeVertex.java
    │   │   ├── ResultSetVertex.java
    │   │   ├── HoverResultVertex.java
    │   │   ├── DefinitionResultVertex.java
    │   │   ├── ReferenceResultVertex.java
    │   │   ├── MonikerVertex.java
    │   │   └── ...
    │   └── edge/
    │       ├── ContainsEdge.java
    │       ├── NextEdge.java
    │       ├── ItemEdge.java
    │       ├── MonikerEdge.java
    │       └── ...
    └── visitor/                      # Визиторы для сбора данных (по аналогии с lsif-java)
        ├── DocumentVisitor.java      # Обход SymbolTree документа
        ├── HoverDataVisitor.java     # Использует HoverProvider
        ├── DefinitionDataVisitor.java
        ├── ReferenceDataVisitor.java
        └── ...
```

### Алгоритм генерации LSIF

1. **Инициализация**
   - Создание вершины `metaData`
   - Создание вершины `project`

2. **Обработка документов**
   Для каждого файла в проекте:
   - Создание вершины `document`
   - Создание вершин `range` для каждого символа
   - Создание ребра `contains` (document → ranges)

3. **Обработка символов**
   Для каждого символа:
   - Создание вершины `resultSet`
   - Создание ребра `next` (range → resultSet)
   - Генерация `hoverResult` и связывающего ребра
   - Генерация `definitionResult` и связывающего ребра (для определений)

4. **Обработка ссылок**
   Для каждой ссылки на символ:
   - Создание `referenceResult` или обновление существующего
   - Создание ребра `item` для связи с конкретным диапазоном

5. **Дополнительные данные**
   - Генерация `foldingRangeResult` для каждого документа
   - Генерация `documentSymbolResult` для каждого документа

## Оценка сложности реализации

### Этап 1: Базовая поддержка (минимальная реализация)

| Компонент | Сложность | Оценка времени |
|-----------|-----------|----------------|
| CLI-команда | Низкая | 2-4 часа |
| Модель данных LSIF | Средняя | 8-16 часов |
| Экспорт документов и диапазонов | Низкая | 4-8 часов |
| Экспорт hover | Низкая | 4-8 часов |
| Экспорт definition | Низкая | 4-8 часов |

**Итого этап 1**: ~30-40 часов

### Этап 2: Полная поддержка

| Компонент | Сложность | Оценка времени |
|-----------|-----------|----------------|
| Экспорт references | Средняя | 8-16 часов |
| Экспорт foldingRange | Низкая | 4-8 часов |
| Экспорт documentSymbol | Низкая | 4-8 часов |
| Поддержка moniker | Высокая | 16-24 часа |
| Тестирование | Средняя | 8-16 часов |
| Документация | Низкая | 4-8 часов |

**Итого этап 2**: ~45-80 часов

### Общая оценка: 75-120 часов

## Потенциальные сложности

### Технические

1. **Управление идентификаторами**
   - LSIF требует уникальные числовые ID для всех элементов
   - Необходимо реализовать генератор ID

2. **Обработка больших проектов**
   - Потенциально большой объём выходных данных
   - Возможна необходимость потоковой записи

3. **Кросс-файловые ссылки**
   - Ссылки между документами требуют правильного порядка обработки
   - Может потребоваться двухпроходная обработка

### Семантические

1. **Moniker (идентификаторы символов)**
   - Для кросс-репозиторной навигации нужны стабильные идентификаторы
   - Необходимо определить схему формирования moniker для BSL

2. **Экспортируемые/неэкспортируемые символы**
   - Нужно учитывать видимость символов при генерации references

## Интеграция с существующими инструментами

### Sourcegraph

Sourcegraph поддерживает LSIF и может использовать сгенерированные индексы для:
- Code Intelligence в веб-интерфейсе
- Кросс-репозиторной навигации
- Поиска по коду

### GitHub Code Navigation

GitHub также поддерживает LSIF для расширенной навигации по коду:
- Переход к определению
- Поиск использований
- Hover-подсказки

## Эталонная реализация: lsif-java

Microsoft предоставляет эталонную реализацию LSIF-индексатора для Java: [microsoft/lsif-java](https://github.com/microsoft/lsif-java).

### Обзор lsif-java

**lsif-java** — это LSIF-индексатор для Java-проектов, построенный на Eclipse JDT. Он демонстрирует рекомендуемый подход к реализации LSIF-экспорта для языкового сервера.

**Требования:**
- JDK 17
- Поддержка Maven и Gradle проектов

> **Примечание для BSL LS:** BSL Language Server также требует JDK 17. Специфических требований к структуре проекта для LSIF-экспорта не будет — достаточно указать путь к каталогу с исходными файлами BSL/OS.

**Использование:**
```sh
./index.bat "-Drepo.path=<путь к Java проекту>"
./index.bat "-Drepo.path=<путь>" "-Doutput.format=json"  # Формат: line (по умолчанию) или json
```

### Архитектура lsif-java

```
lsif-java/
├── cmd/                              # CLI и скрипты запуска
├── com.microsoft.java.lsif.core/     # Основная логика индексации
│   ├── internal/
│   │   ├── indexer/
│   │   │   ├── LsifService.java      # Оркестратор процесса индексации
│   │   │   ├── IndexerContext.java   # Контекст индексации (состояние)
│   │   │   └── Emitter.java          # Генератор LSIF-вывода
│   │   ├── visitors/                 # AST-визиторы
│   │   │   ├── DocumentVisitor.java  # Обход документа
│   │   │   ├── SymbolVisitor.java    # Обработка символов
│   │   │   ├── HoverVisitor.java     # Генерация hover
│   │   │   ├── ReferencesVisitor.java
│   │   │   └── ImplementationsVisitor.java
│   │   └── protocol/                 # Модели LSIF-протокола
│   │       ├── Element.java
│   │       ├── Vertex.java
│   │       ├── Edge.java
│   │       └── ...
├── com.microsoft.java.lsif.product/  # Конфигурация продукта
└── com.microsoft.java.lsif.target/   # Target Platform
```

### Ключевые компоненты lsif-java

| Компонент | Описание | Аналог в BSL LS |
|-----------|----------|-----------------|
| `LsifService` | Оркестратор индексации, координирует обход проекта | Новый `LsifService` |
| `IndexerContext` | Хранит состояние индексации, маппинги, кеши | `ServerContext` |
| `Emitter` | Генерирует LSIF JSON (вершины и рёбра) | Новый `LsifEmitter` |
| `DocumentVisitor` | Обходит AST документа (Eclipse JDT) | Обход `SymbolTree` |
| `HoverVisitor` | Собирает данные для hover | `HoverProvider` |
| `ReferencesVisitor` | Собирает ссылки на символы | `ReferenceIndex` |
| `RangeBuilder` | Формирует диапазоны позиций | `Ranges` утилиты |

### История версий lsif-java

| Версия | Изменения |
|--------|-----------|
| 0.5.2 | Исправление дублирования событий, удаление log4j |
| 0.5.0 | Поддержка `ReferenceResults` и `ReferenceLinks`, ребро `belongsTo` |
| 0.4.0 | Поддержка moniker (LSIF v0.5.0), логические группы проектов |
| 0.3.0 | Обновление до протокола v4 |
| 0.2.0 | Реимплементация для улучшения производительности, протокол v3 |

### Уроки из lsif-java для BSL LS

1. **Паттерн Visitor**
   - lsif-java использует отдельные визиторы для разных типов данных
   - В BSL LS можно использовать существующие провайдеры как "визиторы"

2. **IndexerContext для состояния**
   - Отдельный контекст для хранения промежуточных данных индексации
   - Помогает избежать повторной обработки и дедуплицировать результаты

3. **Потоковый Emitter**
   - Запись LSIF происходит потоково (по мере обхода)
   - Позволяет обрабатывать большие проекты без OOM

4. **Поддержка разных форматов вывода**
   - lsif-java поддерживает `line` (NDJSON) и `json` форматы
   - Рекомендуется реализовать оба для совместимости (см. параметр `--format` выше)

## Рекомендации

1. **Начать с минимальной реализации**
   - Поддержка hover, definition для локальных символов
   - Постепенное добавление references и других возможностей

2. **Использовать существующую инфраструктуру**
   - ReferenceIndex уже содержит необходимые данные
   - Провайдеры уже реализуют логику получения данных

3. **Изучить lsif-java как эталон**
   - Структура кода и паттерны проектирования
   - Обработка граничных случаев (дублирование, большие файлы)
   - Формат вывода и совместимость с инструментами

4. **Тестирование с lsif-util**
   - Microsoft предоставляет утилиту для валидации LSIF-файлов
   - https://github.com/microsoft/lsif-node/tree/main/util

5. **Итеративная разработка**
   - Начать с одного провайдера (например, hover)
   - Добавлять остальные по мере готовности

## Ссылки

- [Спецификация LSIF 0.6.0](https://microsoft.github.io/language-server-protocol/specifications/lsif/0.6.0/specification/)
- [Обзор LSIF](https://microsoft.github.io/language-server-protocol/overviews/lsif/overview/)
- [lsif-java (эталонная реализация для Java)](https://github.com/microsoft/lsif-java)
- [lsif-node (эталонная реализация для TypeScript)](https://github.com/microsoft/lsif-node)
- [LSIF.dev (каталог реализаций)](https://lsif.dev/)
- [Sourcegraph LSIF](https://docs.sourcegraph.com/code_intelligence/explanations/precise_code_intelligence)
