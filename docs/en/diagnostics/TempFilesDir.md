# TempFilesDir() method call (TempFilesDir)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Major` | `Yes` | `5` | `standard`<br/>`badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
When you use GetTemporaryFileName, 1С:Enterprise retains control over these files and by default deletes them as soon as a working process 
(if a file is created on the server side) or client application (if a file is created on the client side) is restarted.

If a temporary file name is generated otherwise, and the application code fails (or is for any other reason unable) to delete a temporary file,
 it is not controlled by the platform and is saved in the file system for an indefinite time. 
 Lost temporary files accumulated in the system can pose a serious problem, specifically for infobases with a great number of active users 
 (for example, in the service mode).
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```
Incorrect:
Directory = TemporaryFilesDirectory();
FileName = String(New UniqueIdentifier) + ".xml";
TemporaryFileName = Directory+ FileName;
Data.Write(TemporaryFileName);

Correct:
TemporaryFileName= GetTemporaryFileName("xml");
Data.Write(TemporaryFileName);
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [Standard: Temporary Files and Directories](https://support.1ci.com/hc/en-us/articles/360011122319-Access-to-the-file-system-from-the-configuration-code)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:TempFilesDir-off
// BSLLS:TempFilesDir-on
```

### Parameter for config

```json
"TempFilesDir": false
```
