# BSL Language Server Configuration

BSL Language Server provides the ability to change the settings using a configuration file in json format.  
The created file must be specified using the key `--configuration` *(or `-c`)* when running BSL Language Server as a console application. If you use the editor / IDE with the BSL Language Server client plugin, place it in accordance with the documentation *(this is usually the root of a project or workspace)*.

If there is no configuration file, an attempt will be made to find the ".bsl-language-server.json" file in "%HOMEPATH%"

## Settings

|Name|Type|Description|
|:--|:-:|:--|
|`language`|`String`|Set the language for displaying diagnosed comments. Supported languages:<br/>* `ru` - for Russian (*default*)<br/>* `en` - for English|
|`codeLens`|`JSON-Object`|Contains the settings for displaying `lens` in advanced code editors/IDEs *(for example, [Visual Studio Code](https://code.visualstudio.com/))*, which displays various information above a block of code. Object properties|
|⤷&nbsp;&nbsp;&nbsp;`parameters`|`JSON-Object`|Collection of lens settings. Collection items are json-objects with the following structure:<br/>* *object key* - string, is lens key<br/>* *object value* - if is boolean, then interpreted as lens off-switch (`false`) or on-switch with default parameters (`true`), if is type `json-object`, collection of lens parameters.|
|&nbsp;&nbsp;&nbsp;⤷&nbsp;&nbsp;&nbsp;`cognitiveComplexity`|`Boolean` or `JSON-Object`|Enables displaying the value of the [cognitive complexity](../diagnostics/CognitiveComplexity.md) of the method over its definition. The default is ` true `. Lens options: `complexityThreshold` - lens response threshold. The default is - `-1`.|
|&nbsp;&nbsp;&nbsp;⤷&nbsp;&nbsp;&nbsp;`cyclomaticComplexity`|`Boolean` or `JSON-Object`|Enables displaying the value of the [cyclomatic complexity](../diagnostics/CyclomaticComplexity.md) of the method over its definition. The default is `true`. Lens options: `complexityThreshold` - lens response threshold. The default is - `-1`.|
|`diagnostics`|`JSON-Object`|Contains diagnostic settings|
|⤷&nbsp;&nbsp;&nbsp;`computeTrigger`|`String`|Event that will trigger the code analysis procedure to diagnose comments. Possible values:<br/>* `onType` -when editing a file (online) ***on large files can significantly slow down editing ***<br/>* `onSave` - when saving a file (*default*)<br/> `never` - analysis will not be performed|
|⤷&nbsp;&nbsp;&nbsp;`ordinaryAppSupport`|`Boolean`|Ordinary client support. Diagnostics will require taking into account the features of a ordinary application. Values:<br/>* `true` - the configuration uses ordinary application *(default)* <br/>* `false` - ignore ordinary application warnings|
|⤷&nbsp;&nbsp;&nbsp;`skipSupport`|`String`|This parameter sets **1C configuration** file skipping mode *(for example files are not analyzed for issues)* which are "on support" from vendor configuration. Possible values:<br/>* `withSupport` - skip all modules set "on support" *(all "locks" types)*<br/>* `withSupportLocked` - skip modules set "on support" with prohibited modification *("yellow closed lock")*<br/>* `never` - skip no modules as support mode is not analyzed *(set by default)*|
|⤷&nbsp;&nbsp;&nbsp;`mode`|`String`|Setting for controlling the diagnostic settings accounting mode. Possible options: <br/>* `OFF` - All diagnostics are considered to be turned off, regardless of their settings. <br/>* `ON` - All diagnostics enabled by default are considered enabled, the rest - depending on personal settings <br/>* `EXCEPT` - All diagnostics other than those specified are considered enabled. <br/>* `ONLY` - Only the specified diagnostics are considered enabled. <br/>* `ALL` - All diagnostics are considered enabled|
|⤷&nbsp;&nbsp;&nbsp;`parameters`|`JSON-Object`|Parameter is a collection of diagnostics parameters. Collection items are json-objects with the following structure:<br/>* *object key* - string, is diagnostic key<br/>* *object value* - if is boolean, then interpreted as diagnostic off-switch (`false`) or on-switch with default parameters (`true`), if is type `json-object`, collection of diagnostic parameters.<br/><br/>Key, if set to ON by default and all allowed parameters and examples are given on the diagnostic page.|
|⤷&nbsp;&nbsp;&nbsp;`subsystemsFilter`|`JSON-Object`|Filter by configuration subsystems|
|⤷&nbsp;&nbsp;&nbsp;`analyzeOnStart`|`Boolean`|Starting the analysis of the entire project at server startup. If enabled, after the context is built on the client, information about diagnostics in all project files will be sent.|
|&nbsp;&nbsp;&nbsp;⤷&nbsp;&nbsp;&nbsp;`include`|`Array` `String`|List of names of subsystems for which objects the analysis is performed, including child subsystems|
|&nbsp;&nbsp;&nbsp;⤷&nbsp;&nbsp;&nbsp;`exclude`|`Array` `String`|List of names of subsystems excluded from analysis, including child subsystems|
|`documentLink`|`JSON-Object`|Contains documentation link settings|
|⤷&nbsp;&nbsp;&nbsp;`showDiagnosticDescription`|`Boolean`|Show additional links to diagnostics documentation. By default, the parameter is off (*set to `false`*)|
|`useDevSite`|`Boolean`|When you turn on the settings, the resulting documentation links will lead to the develop version of the site. By default, the parameter is off (*set to `false`*)|
|`siteRoot`|`String`|The path to the root of the site with the documentation. By default, the parameter value is `"https://1c-syntax.github.io/bsl-language-server"` |
|`traceLog`|`String`|To log all requests *(incoming and outgoing)* between **BSL Language Server** and **Language Client** from used editor/IDE, this parameter sets log file path. The path can set either absolute or relative *(from project root)*, by default the value is not set.<br/><br/>**WARNING**<br/><br/>* When starting **BSL Language Server** overwrites this file <br/>* Speed of interaction between client and server **DRAMATICALLY REDUCED**|
|`configurationRoot`|`String`|This parameter is intended to indicate the root directory the 1C configuration files are located in the project directory. It can be useful if there are several configuration directories in the same project directory or when the structure of the project directory is so complex. By default, the parameter is empty and `BSL Language Server` determines the location of the configuration root directory independently|
|`sendErrors`|`String`|Mode for sending error messages to BSL Language Server developers. More [Monitoring](Monitoring.md).Possible values:<br/>* `ask` - ask permission on every error *(set by default)*. <br/>* `send` - always send error messages.<br/>* `never` - never send error messages.|

You can use the following JSON schema to make it easier to compile and edit a configuration file:

```log
https://1c-syntax.github.io/bsl-language-server/configuration/schema.json
```

## Example

Setting example:

* Language of diagnostics messages - English;
* Changes the diagnostic setting for [LineLength - Line Length limit](../diagnostics/LineLength.md), set the limit for the length of a string to 140 characters;
* Disable [MethodSize - Method size restriction diagnostic](../diagnostics/MethodSize.md).
* Enables the calculation of diagnostics in continuous mode (`computeTrigger = onType`)
* Diagnostics are calculated only for the objects of the "StandardSubsystems" subsystem, with the exception of "ReportVariants" and "VersioningObjects"

```json
{
  "$schema": "https://1c-syntax.github.io/bsl-language-server/configuration/schema.json",
  "language": "en",
  "diagnostics": {
    "computeTrigger": "onType",
    "parameters": {
      "LineLength": {
        "maxLineLength": 140
      },
      "MethodSize": false
    },
    "subsystemsFilter": {
      "include": ["StandardSubsystems"],
      "exclude": ["ReportVariants", "VersioningObjects"]
    }
  }
}
```
