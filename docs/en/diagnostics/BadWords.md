# Запрещенные слова (BadWords)

|      Тип      | Поддерживаются<br>языки | Важность | Включена<br>по умолчанию | Время на<br>исправление (мин) |   Теги   |
|:-------------:|:-----------------------------:|:--------:|:------------------------------:|:-----------------------------------:|:--------:|
| `Дефект кода` |      `BSL`<br>`OS`      | `Важный` |             `Нет`              |                 `1`                 | `design` |

## Параметры


|    Имя     |   Тип    |                  Описание                   | Значение<br>по умолчанию |
|:----------:|:--------:|:-------------------------------------------:|:------------------------------:|
| `badWords` | `Строка` | `Регулярное выражение для слов-исключений.` |              ``              |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
В тексте модулей не должны встречаться запрещенные слова. Список запрещенных слов задается регулярным выражением. Поиск производится без учета регистра символов.

**Примеры настройки:**

"редиска|лопух|экзистенциальность"

"ло(х|шара|шпед)"

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Экранирование кода

```bsl
// BSLLS:BadWords-off
// BSLLS:BadWords-on
```

### Параметр конфигурационного файла

```json
"BadWords": {
    "badWords": ""
}
```