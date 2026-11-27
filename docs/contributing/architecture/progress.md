# Ход упорядочивания архитектуры

Журнал раундов: что сделано, чем проверено, какое ограничение против регрессии добавлено.
Решения — в [decisions.md](decisions.md), открытые задачи — в [backlog.md](backlog.md).

## Исходное состояние

- Карта зависимостей пакетов уже закреплена ArchUnit-правилом с whitelist'ом на каждый пакет;
  известный цикл — `references` ↔ `types`. Ацикличность пакетов вне ядра проверяется отдельно.
- Закреплены конвенции именования (диагностики, провайдеры, репортёры, команды, события),
  запрет stdout/stderr и `java.util.logging`, конструкторное внедрение.
- Инвентаризация энтропии (три независимых сканирования: утилиты, LSP-фичи,
  конфигурация/контекст/CLI/MCP) сведена в [backlog.md](backlog.md).

## Раунд 1. Поиск и чтение файла конфигурации

- **Проблема.** Три копии порядка поиска файла, две копии чтения JSON; битый JSON ронял
  загрузку конфигурации (ловился только `IOException`, а `JacksonException` в Jackson 3 —
  непроверяемое исключение).
- **Сделано.** `ConfigurationFiles.locate/read`; на него переведены
  `LanguageServerConfiguration`, `GlobalLanguageServerConfiguration`,
  `LanguageServerConfigurationFactory`. Удалены дубли маппера и цепочек `if`. См. ADR-0002.
- **Проверка.** Сначала красные тесты на битый и неверно типизированный JSON
  (`LanguageServerConfigurationTest`, `GlobalLanguageServerConfigurationTest`), затем
  контрактный `ConfigurationFilesTest`; тесты пакета `configuration`, `BSLLSBindingTest`,
  `ArchitectureTest` — зелёные.
- **Ограничение.** `ArchitectureTest.configuration_files_are_parsed_only_by_configuration_files`.
