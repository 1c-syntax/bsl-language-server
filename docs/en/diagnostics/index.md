# Diagnostics

Used for code analysis to meet coding standards and search for possible errors.

Some of diagnostics are disabled by default. Use <a href="/#configuration">configuration file</a> to enable them.

To escape individual sections of code or files from triggering diagnostics, you can use special comments of the form `// BSLLS:DiagnosticKey-off` . This functionality is described in more detail in [Escaping sections of code](../features/DiagnosticIgnorance.md) .

## Implemented diagnostics

Total: **63**

* Error: **23**
* Code smell: **38**
* Vulnerability: **2**

| Key | Name| Enabled by default | Severity | Type | Tags |
| --- | --- | :-: | --- | --- | --- |
| [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Violating transaction rules for the 'BeginTransaction' method | Yes | Major | Error | `standard` |
| [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Canonical keyword writing | Yes | Info | Code smell | `standard` |
| [CognitiveComplexity](CognitiveComplexity.md) | Cognitive complexity | Yes | Critical | Code smell | `brainoverload` |
| [CommentedCode](CommentedCode.md) | Commented out code | Yes | Minor | Code smell | `standard`<br/>`badpractice` |
| [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Violating transaction rules for the 'CommitTransaction' method | Yes | Major | Error | `standard` |
| [DeletingCollectionItem](DeletingCollectionItem.md) | Deleting an item when iterating through collection using the operator "For each ... In ... Do" | Yes | Major | Error | `standard`<br/>`error` |
| [DeprecatedFind](DeprecatedFind.md) | Using of the deprecated method "Find" | Yes | Minor | Code smell | `deprecated` |
| [DeprecatedMessage](DeprecatedMessage.md) | Restriction on the use of deprecated "Message" method | Yes | Minor | Code smell | `standard`<br/>`deprecated` |
| [EmptyCodeBlock](EmptyCodeBlock.md) | Empty code block | Yes | Major | Code smell | `badpractice`<br/>`suspicious` |
| [EmptyStatement](EmptyStatement.md) | Empty statement | Yes | Info | Code smell | `badpractice` |
| [ExtraCommas](ExtraCommas.md) | Commas without a parameter at the end of a method call | Yes | Major | Code smell | `standard`<br/>`badpractice` |
| [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | The function should have return | Yes | Major | Error | `suspicious`<br/>`unpredictable` |
| [IdenticalExpressions](IdenticalExpressions.md) | There are identical sub-expressions to the left and to the right of the "foo" operator | Yes | Major | Error | `suspicious` |
| [IfConditionComplexity](IfConditionComplexity.md) | Usage of complex expressions in the "If" condition | Yes | Minor | Code smell | `brainoverload` |
| [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Duplicated code blocks in If...Then...ElseIf... statements | Yes | Minor | Code smell | `suspicious` |
| [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Duplicated conditions in If...Then...ElseIf... statements | Yes | Major | Code smell | `suspicious` |
| [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Else...The...ElseIf... statement should end with Else branch | Yes | Major | Code smell | `badpractice` |
| [InvalidCharacterInFile](InvalidCharacterInFile.md) | Invalid character | Yes | Major | Error | `error`<br/>`standard`<br/>`unpredictable` |
| [LineLength](LineLength.md) | Line Length limit | Yes | Minor | Code smell | `standard`<br/>`badpractice` |
| [MagicNumber](MagicNumber.md) | Magic numbers | Yes | Minor | Code smell | `badpractice` |
| [MethodSize](MethodSize.md) | Method size restriction | Yes | Major | Code smell | `badpractice` |
| [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Missing code in Raise block in "Try ... Raise ... EndTry" | Yes | Major | Error | `standard`<br/>`badpractice` |
| [MissingSpace](MissingSpace.md) | Missing spaces to the left or right of operators + - * / = % < > <> <= >=, and also to the right of , and ; | Yes | Info | Code smell | `badpractice` |
| [MissingTemporaryFileDeletion](MissingTemporaryFileDeletion.md) | Missing temporary file deletion after using | Yes | Major | Error | `badpractice`<br/>`standard` |
| [MultilingualStringHasAllDeclaredLanguages](MultilingualStringHasAllDeclaredLanguages.md) | There is a localized text for all languages declared in the configuration | Yes | Minor | Error | `error` |
| [MultilingualStringUsingWithTemplate](MultilingualStringUsingWithTemplate.md) | There is a localized text for all languages declared in the configuration | Yes | Major | Error | `error` |
| [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Nested constructors with parameters in structure declaration | Yes | Minor | Code smell | `badpractice`<br/>`brainoverload` |
| [NestedStatements](NestedStatements.md) | Control flow statements should not be nested too deep | Yes | Critical | Code smell | `badpractice`<br/>`brainoverload` |
| [NestedTernaryOperator](NestedTernaryOperator.md) | Nested ternary operator | Yes | Major | Code smell | `brainoverload` |
| [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Non export methods in API regions | Yes | Major | Code smell | `standard` |
| [NumberOfOptionalParams](NumberOfOptionalParams.md) | Limit number of optional parameters in method | Yes | Minor | Code smell | `standard`<br/>`brainoverload` |
| [NumberOfParams](NumberOfParams.md) | Number of parameters in method | Yes | Minor | Code smell | `standard`<br/>`brainoverload` |
| [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Limit on the number of property values passed to the structure constructor | Yes | Minor | Code smell | `standard`<br/>`brainoverload` |
| [OneStatementPerLine](OneStatementPerLine.md) | One statement per line | Yes | Minor | Code smell | `standard`<br/>`design` |
| [OrderOfParams](OrderOfParams.md) | Order of Parameters in method | Yes | Major | Code smell | `standard`<br/>`design` |
| [PairingBrokenTransaction](PairingBrokenTransaction.md) | Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" | Yes | Major | Error | `standard` |
| [ParseError](ParseError.md) | Source code parse error | Yes | Critical | Error | `error` |
| [ProcedureReturnsValue](ProcedureReturnsValue.md) | Procedure should not return Value | Yes | Blocker | Error | `error` |
| [SelfAssign](SelfAssign.md) | Variable is assigned to itself | Yes | Major | Error | `suspicious` |
| [SelfInsertion](SelfInsertion.md) | Insert a collection into itself | Yes | Major | Error | `standard`<br/>`unpredictable`<br/>`performance` |
| [SemicolonPresence](SemicolonPresence.md) | Statement should end with semicolon symbol ";" | Yes | Minor | Code smell | `standard`<br/>`badpractice` |
| [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Erroneous indication of several compilation directives | Yes | Critical | Error | `unpredictable`<br/>`error` |
| [SpaceAtStartComment](SpaceAtStartComment.md) | Space at the beginning of the comment | Yes | Info | Code smell | `standard` |
| [TernaryOperatorUsage](TernaryOperatorUsage.md) | Ternary operator usage | No | Minor | Code smell | `brainoverload` |
| [TryNumber](TryNumber.md) | Cast to number of try catch block | Yes | Major | Code smell | `standard` |
| [UnaryPlusInConcatenation](UnaryPlusInConcatenation.md) | Unary Plus sign in string concatenation | Yes | Blocker | Error | `suspicious`<br/>`brainoverload` |
| [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Unknown preprocessor symbol | Yes | Critical | Error | `standard`<br/>`error` |
| [UnreachableCode](UnreachableCode.md) | Unreachable Code | Yes | Minor | Error | `design`<br/>`suspicious` |
| [UnusedLocalMethod](UnusedLocalMethod.md) | Unused local method | Yes | Major | Code smell | `standard`<br/>`suspicious` |
| [UseLessForEach](UseLessForEach.md) | Useless collection iteration | Yes | Critical | Error | `clumsy` |
| [UsingCancelParameter](UsingCancelParameter.md) | Using parameter "Cancel" | Yes | Major | Code smell | `standard`<br/>`badpractice` |
| [UsingFindElementByString](UsingFindElementByString.md) | Using FindByName and FindByCode | Yes | Major | Code smell | `standard`<br/>`badpractice`<br/>`performance` |
| [UsingGoto](UsingGoto.md) | "goto" statement should not be used | Yes | Critical | Code smell | `standard`<br/>`badpractice` |
| [UsingHardcodeNetworkAddress](UsingHardcodeNetworkAddress.md) | Using hardcode ip addresses in code | Yes | Critical | Vulnerability | `standard` |
| [UsingHardcodePath](UsingHardcodePath.md) | Using hardcode file paths and ip addresses in code | Yes | Critical | Error | `standard` |
| [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Storing confidential information in code | Yes | Critical | Vulnerability | `standard` |
| [UsingModalWindows](UsingModalWindows.md) | Using modal windows | No | Major | Code smell | `standard` |
| [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Using unavailable in Unix objects | Yes | Critical | Error | `standard`<br/>`lockinos` |
| [UsingServiceTag](UsingServiceTag.md) | Using service tags | Yes | Info | Code smell | `badpractice` |
| [UsingSynchronousCalls](UsingSynchronousCalls.md) | Using synchronous calls | No | Major | Code smell | `standard` |
| [UsingThisForm](UsingThisForm.md) | Using deprecated property "ThisForm" | Yes | Minor | Code smell | `standard`<br/>`deprecated` |
| [WorkingTimeoutWithExternalResources](WorkingTimeoutWithExternalResources.md) | Timeouts working with external resources | Yes | Critical | Error | `unpredictable`<br/>`standard` |
| [YoLetterUsage](YoLetterUsage.md) | Using Russian character "yo" ("ё") in code | Yes | Info | Code smell | `standard` |