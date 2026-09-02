# Frequently asked questions

This section contains the most frequently asked questions and answers.

## How to change the language of diagnostic messages?

To change the language of displayed messages from Russian (default) to English, you can use the configuration file. The configuration options can be found in the [description of configuration file.](features/ConfigurationFile.md)

## How to increase the maximum line length in diagnostics `Line length limit`?

All diagnostics has a page with a description in the `Diagnostics` section, for example, [Line length limit](diagnostics/LineLength.md). If the diagnosis can change its behavior, then on the description page there will be parameters with a description of what they affect. In this case, the `maxLineLength` parameter is responsible for the maximum line length.

## How to disable Lens on cognitive complexity?

To disable the `lens` with information about cognitive complexity, you should use the configuration file in which `showCognitiveComplexity` set to `false`. The configuration options can be found in the [description of the configuration file.](features/ConfigurationFile.md)

## I think that the diagnosis does not work correctly. What to do?

In case of doubt (or confidence) that the diagnosis is not working correctly, there are two ways

- contact the [telegram](https://t.me/bsl_language_server) chat and describe situation, maybe still there is no error
- create issue ([issue](https://github.com/1c-syntax/bsl-language-server/issues)) in the project repository of the appropriate type, where to attach the error description and, it is very desirable, code examples where the diagnosis does not work correctly.

## `BSL Language Server` and `SonarQube` plugin are the same things?

`BSL Language Server` is a stand-alone application, the implementation of the server side of the LSP protocol. The plugin for `SonarQube` uses the `BSL Language Server` to analyze the BSL source code (1C configurations, 1Script and 1Script.Web scripts).

`BSL Language Server` can be used with any application that has an LSP client implementation. Verified connections:

* plugin for [VS code](https://github.com/1c-syntax/vsc-language-1c-bsl/);
* additional utility for 1C:Enterprise in DESIGNER mode [Phoenix BSL](https://github.com/otymko/phoenixbsl);
* plugin for [Sublime Text](https://github.com/sublimelsp/LSP).

Also through direct import `BSL Language Server` work:

* plugin for [1C: Enterprise development tools](https://github.com/DoublesunRUS/ru.capralow.dt.bslls.validator) (own implementation, without LSP);
* plugin for [SonarQube](https://github.com/1c-syntax/sonar-bsl-plugin-community);
