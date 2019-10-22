# Diagnostics

Used for code analysis to meet coding standards and search for possible errors.

Some of diagnostics are disabled by default. Use <a href="/#configuration">configuration file</a> to enable them.

To escape individual sections of code or files from triggering diagnostics, you can use special comments of the form `// BSLLS:DiagnosticKey-off` . This functionality is described in more detail in [Escaping sections of code](../features/DiagnosticIgnorance.md) .

## Implemented diagnostics

| Key | Name| Enabled by default | Tags |
| --- | --- | :-: | --- |
| [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Violating transaction rules for the 'BeginTransaction' method | Yes | `standard` |
| [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Canonical spelling of keywords | Yes | `standard` |
| [CognitiveComplexity](CognitiveComplexity.md) | Cognitive complexity | Yes | `brainoverload` |
| [CommentedCode](CommentedCode.md) | Commented out code | Yes | `standard`<br/>`badpractice` |
| [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Violating transaction rules for the 'CommitTransaction' method | Yes | `standard` |
| [DeletingCollectionItem](DeletingCollectionItem.md) | Deleting an item when iterating through collection using the operator "For each ... In ... Do" | Yes | `standard`<br/>`error` |
| [DeprecatedMessage](DeprecatedMessage.md) | Restriction on the use of deprecated "Message" method | Yes | `standard`<br/>`deprecated` |
| [EmptyCodeBlock](EmptyCodeBlock.md) | Empty code block | Yes | `badpractice`<br/>`suspicious` |
| [EmptyStatement](EmptyStatement.md) | Empty statement | Yes | `badpractice` |
| [ExtraCommas](ExtraCommas.md) | Extra commas when calling a method | Yes | `standard`<br/>`badpractice` |
| [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | Function must have Return statement | Yes | `suspicious`<br/>`unpredictable` |
| [IdenticalExpressions](IdenticalExpressions.md) | There are identical sub-expressions to the left and to the right of the "foo" operator | Yes | `suspicious` |
| [IfConditionComplexity](IfConditionComplexity.md) | If condition is too complex | Yes | `brainoverload` |
| [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Duplicated code blocks in If...Then...ElsIf... | Yes | `suspicious` |
| [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Duplicated conditions in If...Then...ElsIf... | Yes | `suspicious` |
| [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Using If...Then...ElsIf... statement | Yes | `badpractice` |
| [LineLength](LineLength.md) | Line length restriction | Yes | `standard`<br/>`badpractice` |
| [MagicNumber](MagicNumber.md) | Using magic number | Yes | `badpractice` |
| [MethodSize](MethodSize.md) | Method size restriction | Yes | `badpractice` |
| [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Missing code in Raise block in "Try ... Raise ... EndTry" | Yes | `standard`<br/>`badpractice` |
| [MissingSpace](MissingSpace.md) | Missing space | Yes | `badpractice` |
| [MissingTemporaryFileDeletion](MissingTemporaryFileDeletion.md) | Missing temporary file deletion after using | Yes | `badpractice`<br/>`standard` |
| [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Nested constructors with parameters in structure declaration | Yes | `badpractice`<br/>`brainoverload` |
| [NestedStatements](NestedStatements.md) | Control flow statements should not be nested too deep | Yes | `badpractice`<br/>`brainoverload` |
| [NestedTernaryOperator](NestedTernaryOperator.md) | Nested ternary operator | Yes | `brainoverload` |
| [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Non export methods in API regions | Yes | `standard` |
| [NumberOfOptionalParams](NumberOfOptionalParams.md) | Limit number of optional parameters in method | Yes | `standard`<br/>`brainoverload` |
| [NumberOfParams](NumberOfParams.md) | Number of method parameters restriction | Yes | `standard`<br/>`brainoverload` |
| [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Number of values in structure constructor restriction | Yes | `standard`<br/>`brainoverload` |
| [OneStatementPerLine](OneStatementPerLine.md) | One statement per line | Yes | `standard`<br/>`design` |
| [OrderOfParams](OrderOfParams.md) | Order of method parameters | Yes | `standard`<br/>`design` |
| [PairingBrokenTransaction](PairingBrokenTransaction.md) | Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" | Yes | `standard` |
| [ParseError](ParseError.md) | Error parsing source code | Yes | `error` |
| [ProcedureReturnsValue](ProcedureReturnsValue.md) | Procedure must have no Return value | Yes | `error` |
| [SelfAssign](SelfAssign.md) | Variable self assignment | Yes | `suspicious` |
| [SelfInsertion](SelfInsertion.md) | Insert a collection into itself | Yes | `standard`<br/>`unpredictable`<br/>`performance` |
| [SemicolonPresence](SemicolonPresence.md) | Statement should end with ";" | Yes | `standard`<br/>`badpractice` |
| [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Erroneous indication of several compilation directives | Yes | `unpredictable`<br/>`error` |
| [SpaceAtStartComment](SpaceAtStartComment.md) | Space at the beginning of the comment | Yes | `standard` |
| [TernaryOperatorUsage](TernaryOperatorUsage.md) | Ternary operator usage | No | `brainoverload` |
| [TryNumber](TryNumber.md) | Cast to number in try catch block | Yes | `standard` |
| [UnaryPlusInConcatenation](UnaryPlusInConcatenation.md) | Unary Plus sign in string concatenation | Yes | `suspicious`<br/>`brainoverload` |
| [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Unknown preprocessor symbol | Yes | `standard`<br/>`error` |
| [UnreachableCode](UnreachableCode.md) | Unreachable Code | Yes | `design`<br/>`suspicious` |
| [UseLessForEach](UseLessForEach.md) | Useless For Each loop | Yes | `clumsy` |
| [UsingCancelParameter](UsingCancelParameter.md) | Using "Cancel" parameter | Yes | `standard`<br/>`badpractice` |
| [UsingFindElementByString](UsingFindElementByString.md) | Restriction on the use of "FindByDescription" and "FindByCode" methods | Yes | `standard`<br/>`badpractice`<br/>`performance` |
| [UsingGoto](UsingGoto.md) | "Goto" usage | Yes | `standard`<br/>`badpractice` |
| [UsingHardcodePath](UsingHardcodePath.md) | Using hardcode file paths and ip addresses in code | Yes | `standard` |
| [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Storing confidential information in code | Yes | `standard` |
| [UsingModalWindows](UsingModalWindows.md) | Using modal windows | No | `standard` |
| [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Using of objects not available in Unix | Yes | `standard`<br/>`lockinos` |
| [UsingServiceTag](UsingServiceTag.md) | Using service tags | Yes | `badpractice` |
| [UsingSynchronousCalls](UsingSynchronousCalls.md) | Using synchronous calls | No | `standard` |
| [UsingThisForm](UsingThisForm.md) | Using the "ThisForm" property | Yes | `standard`<br/>`deprecated` |
| [WorkingTimeoutWithExternalResources](WorkingTimeoutWithExternalResources.md) | Timeouts working with external resources | Yes | `unpredictable`<br/>`standard` |
| [YoLetterUsage](YoLetterUsage.md) | Using "Ё" letter in code | Yes | `standard` |