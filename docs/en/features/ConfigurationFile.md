# BSL Language Server Configuration

BSL Language Server provides the ability to change the settings using a configuration file in json format. The created file must be specified using the key `--configuration ` *(or `-c`)*  when running BSL Language Server as a console application. If you use the editor / IDE with the BSL Language Server client plugin, place it in accordance with the documentation *(this is usually the root of a project or workspace)*.

## Settings

Name | Type | Description
:-- | :-: | :--
`language` | `String` | Set the language for displaying diagnosed comments. Supported languages:<br>* `ru` - for Russian (*default*)<br>* `en` - for English
`codeLens` | `JSON-Object` | Contains the settings for displaying `lens` in advanced code editors / IDEs *(for example, [Visual Studio Code](https://code.visualstudio.com/) )* , which displays various information above a block of code.
⤷   `showCognitiveComplexity` | `Boolean` | In code editors/IDE with support codelens*(for example [Visual Studio Code](https://code.visualstudio.com/))*, enables displaying the value[ of the cognitive complexity](../diagnostics/CognitiveComplexity.md) of the method over its definition. By default is enabled (*is set to `true`*)
⤷   `showCyclomaticComplexity` | `Boolean` | Similar to `showCognitiveComplexityCodeLens`, enables the display of the [cyclomatic complexity](../diagnostics/CyclomaticComplexity.md) value   of the method. By default enabled (*is set to `true`*)
`diagnostics` | `JSON-Object` | Contains diagnostic settings
⤷   `computeTrigger` | `String` | Event that will trigger the code analysis procedure to diagnose comments. Possible values:<br>* `onType` -when editing a file (online) ***on large files can significantly slow down editing ***<em data-md-type="raw_html"><br> <code data-md-type="raw_html">onSave</code> - when saving a file (<em data-md-type="raw_html">default</em>)</em><br> `never` - analysis will not be performed
⤷   `skipSupport` | `String` | This parameter sets **1C configuration** file skipping mode *(for example files are not analyzed for issues)* which are "on support" from vendor configuration. Possible values:<br>* `withSupport` - skip all modules set "on support" *(all "locks" types)*<br>* `withSupportLocked` -  skip modules set "on support" with prohibited modification *("yellow  closed lock")*<br>* `never` - skip no modules as support mode is not analyzed *(set by default)*
⤷   `mode` | `String` | Setting for controlling the diagnostic settings accounting mode. Possible options: <br> * `OFF` - All diagnostics are considered to be turned off, regardless of their settings. <br> * `ON` - All diagnostics enabled by default are considered enabled, the rest - depending on personal settings <br> * `EXCEPT` - All diagnostics other than those specified are considered enabled. <br> * `ONLY` - Only the specified diagnostics are considered enabled. <br> * `ALL` - All diagnostics are considered enabled.
⤷   `parameters` | `JSON-Object` | Parameter is a collection of diagnostics parameters.  Collection items are json-objects with the following structure:<br>* *object key* - string, is diagnostic key<br>* *object value* - if is boolean, then interpreted as diagnostic off-switch (`false`) or on-switch with default parameters (`true`), if is type  `json-object`,  collection of diagnostic parameters.<br><br>Key, if set to ON by default and all allowed parameters and examples are given on the diagnostic page.
`documentLink` | `JSON-Object` | Contains documentation link settings
⤷   `useDevSite` | `Boolean` | When you turn on the settings, the resulting documentation links will lead to the develop version of the site. By default, the parameter is off ( *set to `false`* )
⤷   `siteRoot` | `String` | The path to the root of the site with the documentation. By default, the parameter value is `"https://1c-syntax.github.io/bsl-language-server"`
`traceLog` | `String` | To log all requests *(incoming and outgoing)* between **BSL Language Server** and **Language Client**  from used editor/IDE. this parameter sets log file path. The path can set either absolute or relative *(from project root)*, by default the value is not set.<br><br>**WARNING**<br><br>* When starting **BSL Language Server** overwrites this file <br>* Speed of interaction between client and server **DRAMATICALLY REDUCED**
`configurationRoot` | `String` | This parameter is intended to indicate the root directory the 1C configuration files are located in the project directory. It can be useful if there are several configuration directories in the same project directory or when the structure of the project directory is so complex. By default, the parameter is empty and `BSL Language Server` determines the location of the configuration root directory independently

You can use the following JSON schema to make it easier to compile and edit a configuration file:

```
https://1c-syntax.github.io/bsl-language-server/configuration/schema.json
```

## Example

The following is an example of a settings:

- Language of diagnostics messages - English;
- Changes the diagnostic setting for [LineLength - Line Length limit](../diagnostics/LineLength.md), set the limit for the length of a string to 140 characters;
- Disable [MethodSize - Method size restriction{/a0 } diagnostic.](../diagnostics/MethodSize.md)
- Enables the calculation of diagnostics in continuous mode ( `computeTrigger = onType` )

```json
{
  "$schema": "https://1c-syntax.github.io/bsl-language-server/configuration/schema.json",
  "diagnosticLanguage": "en",
  "diagnostics": {
    "LineLength": {
      "maxLineLength": 140
    },
    "MethodSize": false
  }
}
```
