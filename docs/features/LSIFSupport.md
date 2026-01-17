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

### Новый CLI-модуль

По аналогии с существующими командами (`analyze`, `format`), предлагается добавить команду `lsif`:

```sh
java -jar bsl-language-server.jar lsif --srcDir ./src/cf --output ./dump.lsif
```

### Структура модулей

```
src/main/java/com/github/_1c_syntax/bsl/languageserver/
├── cli/
│   └── LsifCommand.java          # CLI-команда для генерации LSIF
└── lsif/
    ├── LsifIndexer.java          # Основной класс индексации
    ├── LsifEmitter.java          # Генератор LSIF-вывода
    ├── model/
    │   ├── Element.java          # Базовый интерфейс элемента
    │   ├── Vertex.java           # Интерфейс вершины
    │   ├── Edge.java             # Интерфейс ребра
    │   ├── vertices/             # Реализации вершин
    │   │   ├── MetaData.java
    │   │   ├── Project.java
    │   │   ├── Document.java
    │   │   ├── Range.java
    │   │   ├── ResultSet.java
    │   │   ├── HoverResult.java
    │   │   ├── DefinitionResult.java
    │   │   ├── ReferenceResult.java
    │   │   └── ...
    │   └── edges/                # Реализации рёбер
    │       ├── Contains.java
    │       ├── Next.java
    │       ├── Item.java
    │       └── ...
    └── supplier/                 # Поставщики данных
        ├── LsifDataSupplier.java
        ├── HoverDataSupplier.java
        ├── DefinitionDataSupplier.java
        ├── ReferenceDataSupplier.java
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

## Рекомендации

1. **Начать с минимальной реализации**
   - Поддержка hover, definition для локальных символов
   - Постепенное добавление references и других возможностей

2. **Использовать существующую инфраструктуру**
   - ReferenceIndex уже содержит необходимые данные
   - Провайдеры уже реализуют логику получения данных

3. **Тестирование с lsif-util**
   - Microsoft предоставляет утилиту для валидации LSIF-файлов
   - https://github.com/microsoft/lsif-node/tree/main/util

4. **Итеративная разработка**
   - Начать с одного провайдера (например, hover)
   - Добавлять остальные по мере готовности

## Ссылки

- [Спецификация LSIF 0.6.0](https://microsoft.github.io/language-server-protocol/specifications/lsif/0.6.0/specification/)
- [Обзор LSIF](https://microsoft.github.io/language-server-protocol/overviews/lsif/overview/)
- [lsif-node (эталонная реализация)](https://github.com/microsoft/lsif-node)
- [LSIF.dev (каталог реализаций)](https://lsif.dev/)
- [Sourcegraph LSIF](https://docs.sourcegraph.com/code_intelligence/explanations/precise_code_intelligence)
