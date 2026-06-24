# Навигация и find-references для членов, разрешаемых через вывод типов

> Рабочие заметки сессии. Контекст: почему F12 не работает на `ИмеетТип`
> в `Ожидаем.Что(Переменная).ИмеетТип(...)` (библиотека `asserts`), и можно ли
> завести такие обращения в find-references/rename. Это **анализ и дизайн**,
> кода пока не написано.

## 1. Исходная проблема

В модуле с `#использовать asserts`:

```bsl
Ожидаем.Что(Переменная).ИмеетТип("ДанныеСоставнойФормы");
```

- **Hover** на `ИмеетТип` показывает богатую подсказку. ✅
- **F12 / go-to-definition** на `ИмеетТип` молча ничего не делает. ❌

## 2. Почему ховер есть, а F12 нет

Оба провайдера зовут один и тот же `referenceResolver.findReference(...)` и
получают одну и ту же ссылку — расходятся в постобработке.

Ссылку на `ИмеетТип` отдаёт **`PlatformMemberReferenceFinder` (@Order(200))**
(`references/PlatformMemberReferenceFinder.java:59-77`). Он резолвит член через
тип, который вернул `Ожидаем.Что(Переменная)`, т.е. через
`TypeService.memberAt(...)`, и оборачивает результат в синтетический
**`PlatformMemberSymbol`**. Этот символ:

- `implements Symbol` напрямую — **не** `SourceDefinedSymbol`;
- в дерево символов не входит (`accept()` — no-op, `types/symbol/PlatformMemberSymbol.java:70-72`);
- несёт `MemberDescriptor` (имя, сигнатуры, описание).

Дальше:

- **Hover** подбирает `MarkupContentBuilder` по классу символа → для
  `PlatformMemberSymbol` есть builder → строит подсказку. ✅
- **Definition** (`providers/DefinitionProvider.java:112-116`) фильтрует
  `Reference::isSourceDefinedSymbolReference` (т.е. `symbol instanceof
  SourceDefinedSymbol`). `PlatformMemberSymbol` фильтр не проходит →
  `Collections.emptyList()` → F12 молча ничего не делает. ❌

Почему ссылка не приходит из индекса (где лежат `SourceDefinedSymbol`):
`ReferenceIndexReferenceFinder` (@Order(40), идёт первым) находит только то,
что записал `ReferenceIndexFiller`. Вызовы членов на типе произвольного
выражения он не индексирует — поэтому до `ИмеетТип` доходит только finder №200.

## 3. Ключевая находка: sourceSymbol уже заполнен

`MemberDescriptor` имеет поле `@Nullable Symbol sourceSymbol`
(`types/model/MemberDescriptor.java:66`) + `getSourceSymbol()` / `withSourceSymbol()`.

И главное — для метода OS-library класса дескриптор **уже несёт** этот символ.
`types/oscript/OScriptModuleMembersProvider.java:261-294`, метод
`toMemberDescriptor(MethodSymbol)`:

```java
return MemberDescriptor.method(method.getName(), purpose, List.of(signature))
  .withSourceSymbol(method);   // ← реальный MethodSymbol прикреплён
```

`PlatformMemberReferenceFinder` кладёт весь `member.descriptor()` в
`PlatformMemberSymbol` (строка 68), а `PlatformMemberSymbol.getDescriptor()`
открыт через `@Getter`. Значит `platformMemberSymbol.getDescriptor().getSourceSymbol()`
доступен «как есть».

**Вывод:** для F12 вся информация уже на руках в момент запроса. Не хватает
только того, чтобы `DefinitionProvider` её прочитал.

### Минимальный фикс F12 (подмножество задачи)

В `DefinitionProvider.findLocationLinks`: если reference не
`isSourceDefinedSymbolReference`, но это `PlatformMemberSymbol`, у которого
`descriptor.getSourceSymbol() instanceof SourceDefinedSymbol` — строить
`LocationLink` на него. Платформенные члены без sourceSymbol остаются
hover-only (корректно). Индексатор и типовую систему не трогает.

## 4. Настоящая цель: find-references / rename

Hover/F12 точечны. Хочется, чтобы **find-references на `MethodSymbol.ИмеетТип`**
находил все call-сайты вида `Ожидаем.Что(...).ИмеетТип(...)`. И rename за ним.

Как это работает сейчас (`providers/ReferencesProvider.java:61-81`):

```
resolve символ под курсором → referenceIndex.getReferencesTo(symbol)
```

`ReferenceIndex.getReferencesTo` (`references/ReferenceIndex.java:76-102`) — это
**чистый lookup по координате** `(mdoRef, moduleType, scopeName, kind, name)`,
без инференса. Значит, чтобы find-references нашёл fluent-call-сайты, occurrence'ы
для них должны лежать в индексе под ключом символа `ИмеетТип`.

Координата для метода OS-класса уже определена и используется
(`ReferenceIndexFiller.processLibraryClassAccessCall`):
`mdoRef = <uri .os>, moduleType = OScriptClass, name`. Не пишется только то, что
filler не может вычислить владельца без инференса типа ресивера.

## 5. Тупик №1: lazy-scan на запросе — НЕ масштабируется

Идея «не индексировать, а на find-references прогнать инференс по bucket'у
кандидатов с нужным именем» отвергнута. Если 10 000 файлов вызывают `ИмеетТип`
через `Ожидаем.Что()`, то это 10 000 проходов инференсера **на каждый**
`textDocument/references` — секунды/минуты на разовую операцию. Нежизнеспособно.

## 6. Тупик №2 (мой ранний неверный аргумент)

Сначала я отговаривал от инференса в filler'е под предлогом «стоимость инференса».
Это неверная посылка: 10 000 ресиверов придётся вывести в любом случае. Вопрос не
«платить или нет», а **когда** платить — один раз при построении индекса
(амортизированно, инкрементально по документу) или каждый раз на запросе. Первое —
единственный масштабируемый вариант.

## 7. Вывод: eager-индекс с типовой координатой

Резолв владельца **обязан** быть предпосчитан и лежать в индексе как координата,
иначе find-references перестаёт быть lookup'ом. Раз координату
`.ИмеетТип → тип-владелец` без инференса не получить — **инференс отрабатывает
при наполнении индекса**, не на запросе.

### Модель координаты

Хранить occurrence не как `(mdoRef владельца, …)`, а как
**`(receiver TypeRef, имя члена)`** — это ровно то, что инференсер отдаёт напрямую,
без обратного мэппинга «тип → mdoRef модуля». На запросе `getReferencesTo(S)` для
метода OS-класса выводим объявляющий тип `T` символа `S` (обратный индекс
`uri → TypeRef` уже есть — `GlobalScopeProvider.indexModuleType`, см.
`OScriptModuleMembersProvider.java:155`) и делаем lookup по `(T, name)`.
Симметрично записи, по-прежнему O(lookup).

### Что реально стоит денег — НЕ CPU на запрос, а инвалидация

Текущий индекс дёшев и корректен потому, что координата вычисляется из
**локального синтаксиса** (`ОбщийМодуль.Метод` — mdoRef прямо из имени в коде) и
не зависит от содержимого других документов. Типозависимая координата ломает этот
инвариант. Три источника устаревания:

1. **Cold-start ordering.** Документ A может индексироваться раньше, чем
   зарегистрирован тип asserts-класса B → инференс вернёт UNKNOWN → call-сайт не
   попадёт в индекс.
   → Лечится полным проходом по `ServerContextPopulatedEvent`
   (`context/events/ServerContextPopulatedEvent.java` — публикуется после загрузки
   и индексации всех документов; граф типов на нём устаканен).

2. **Инкрементальная правка A.** Перезаполняем исходящие member-call-ссылки самого
   A — граф B уже готов. Это текущая модель filler'а + инференс member-доступов.

3. **Правка типа-определения B.** ВАЖНО (правка раннего вывода): типы, на чьи
   члены ссылаются call-сайты, — это в первую очередь **собственный код проекта**,
   а он правится постоянно. Поэтому «принять устаревание, библиотеки меняются редко»
   — неверно: инвалидация по этому случаю **обязательна**, не откладывается.

   Но не всякая правка B инвалидирует координату caller'а A. Координата —
   `(receiver TypeRef T, имя M)`, где `M` берётся из синтаксиса A.

   - **Правка тела метода B** (большинство кейстроков) — координаты не меняет.
     Сурфейс не затронут. (Члены и так берутся лениво из живого symbol-tree:
     `OScriptModuleMembersProvider.registerMemberSource(ref, () -> collectMembers)`.)
   - **Переименование члена в B** (`ИмеетТип`→`Equals`) — переиндексации A **не
     требует**, ровно как у обычных вызовов: имя `M` в координате синтаксическое.
     `getReferencesTo(новый "Equals")` строит ключ `(T,"equals")` → occurrence A
     `(T,"имееттип")` не матчится → корректно исключён; старого символа нет, вызов
     в A — висячая ссылка, и индекс правильно её ни с чем не вяжет. Самоисцеление.
   - **Изменение возвращаемого типа / тип-сурфейса, влияющего на инференс ресивера**
     (например, сменили `@return` у `Что()`) — ЕДИНСТВЕННОЕ, что реально
     инвалидирует: `TypeOf(Ожидаем.Что(X))` стал другим → сохранённый `T` в A
     устарел.

   → Обязательный, но **узкий** dependency-инвалидатор: «сменился возвращаемый
   тип/сурфейс типа B → переиндексировать документы, чьи координаты имеют
   `receiver TypeRef ∈ типы B`». Referrer'ов перечисляет сам индекс (он кейован по
   `receiver TypeRef`) — переиндексация хирургическая, не «весь воркспейс».
   Триггер — дешёвый хэш экспортного сурфейса (имена+сигнатуры+возвращаемые типы),
   сверяемый при перерегистрации member-source.

   Разная толерантность по фичам:
   - **rename** — staleness недопустим (пропуск call-сайта = тихо сломанный код) →
     инвалидация по сурфейсу обязательна;
   - **find-references** — read-only/advisory → допускает best-effort до следующего
     касания файла, если режем объём первой итерации.

## 8. Итоговая рамка

- find-references/rename через инференс — **только eager-индекс с типовой
  координатой**. Lazy отпадает.
- Неизбежная цена — один инференс на member-call-сайт при построении/populate и
  при правке самого документа (линейно по числу обращений к членам; для 10k файлов —
  заметный, но единоразовый бюджет; стоит гейтить настройкой).
- Главная инженерная задача — **инвалидация при изменении типов**, а не скорость
  запроса.

## 9. Предлагаемый объём фичи (черновик плана)

1. Типовая координата `(receiver TypeRef, member name)` в occurrence-модели
   (`references/model/Symbol`, репозитории occurrence'ов).
2. Инференс member-доступов в `ReferenceIndexFiller` + полный проход на
   `ServerContextPopulatedEvent`.
3. Узкий dependency-инвалидатор по сурфейсу типа (см. №8/случай 3): хэш экспортного
   сурфейса при перерегистрации member-source → переиндексация только referrer'ов с
   `receiver TypeRef ∈ типы изменённого типа`. Для rename — обязательно.
4. `DefinitionProvider` — F12 как частный случай (читает
   `PlatformMemberSymbol.descriptor.sourceSymbol`).
5. Гейт по настройке + перф-замер на крупном проекте.

### Открытый вопрос (требует решения перед реализацией)

Объём первой итерации для find-references (НЕ rename — там инвалидация обязательна):
- (a) find-references best-effort, без сурфейс-инвалидации (проще, возможен пропуск
  call-сайта до касания файла), или
- (b) сразу полная сурфейс-инвалидация и для find-references (корректнее, дороже)?

Решено по существу: «accept устаревание, библиотеки редко» снято — референсятся в
первую очередь типы собственного кода проекта, который правится постоянно, поэтому
сурфейс-инвалидация обязательна как минимум для rename.

## Карта задействованных файлов

| Файл | Роль |
|------|------|
| `references/PlatformMemberReferenceFinder.java:59-77` | @Order(200), оборачивает член в `PlatformMemberSymbol` |
| `types/symbol/PlatformMemberSymbol.java` | синтетический symbol, не `SourceDefinedSymbol`, несёт `MemberDescriptor` |
| `providers/DefinitionProvider.java:112-116` | фильтр `isSourceDefinedSymbolReference` → отсекает F12 |
| `providers/HoverProvider.java` | строит подсказку по классу символа (работает) |
| `types/model/MemberDescriptor.java:66` | поле `sourceSymbol` + `withSourceSymbol`/`getSourceSymbol` |
| `types/oscript/OScriptModuleMembersProvider.java:261-294` | `.withSourceSymbol(method)` — уже заполняет sourceSymbol |
| `references/ReferenceIndexFiller.java` | синтаксический filler; статические паттерны `Новый Класс()` / `ОбщийМодуль("X")` |
| `references/ReferenceIndex.java:76-102,184-240` | `getReferencesTo` (lookup по координате), `addMethodCall` |
| `references/ReferenceIndexReferenceFinder.java` | @Order(40), lookup в индексе |
| `providers/ReferencesProvider.java:61-81` | find-references = resolve + `getReferencesTo` |
| `types/TypeService.java:445-477` | `memberAt`/`membersAt`, `definingSymbol`, `definingUri` |
| `context/events/ServerContextPopulatedEvent.java` | фаза «всё загружено» для полного прохода |
| `types/registry/GlobalScopeProvider` | `indexModuleType(uri, ref)` — обратный индекс uri→TypeRef |
