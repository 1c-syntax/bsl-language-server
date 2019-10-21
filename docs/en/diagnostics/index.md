# Diagnostics

Used for code analysis to meet coding standards and search for possible errors.

Some of diagnostics are disabled by default. Use <a href="#configuration">configuration file</a> to enable them.

To escape individual sections of code or files from triggering diagnostics, you can use special comments of the form `// BSLLS:DiagnosticKey-off` . This functionality is described in more detail in [Escaping sections of code](../features/DiagnosticIgnorance.md) .

## Implemented diagnostics

| Key | Name| Enabled by default |
| --- | --- | :-: |
| [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Violating transaction rules for the 'BeginTransaction' method | Yes |
| [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Canonical spelling of keywords | Yes |
| [CognitiveComplexity](CognitiveComplexity.md) | Cognitive complexity | Yes |
| [CommentedCode](CommentedCode.md) | Commented out code | Yes |
| [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Violating transaction rules for the 'CommitTransaction' method | Yes |
| [DeletingCollectionItem](DeletingCollectionItem.md) | Deleting an item when iterating through collection using the operator "For each ... In ... Do" | Yes |
| [DeprecatedMessage](DeprecatedMessage.md) | Restriction on the use of deprecated "Message" method | Yes |
| [EmptyCodeBlock](EmptyCodeBlock.md) | Empty code block | Yes |
| [EmptyStatement](EmptyStatement.md) | Empty statement | Yes |
| [ExtraCommas](ExtraCommas.md) | Extra commas when calling a method | Yes |
| [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | Function must have Return statement | Yes |
| [IdenticalExpressions](IdenticalExpressions.md) | There are identical sub-expressions to the left and to the right of the "foo" operator | Yes |
| [IfConditionComplexity](IfConditionComplexity.md) | If condition is too complex | Yes |
| [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Duplicated code blocks in If...Then...ElsIf... | Yes |
| [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Duplicated conditions in If...Then...ElsIf... | Yes |
| [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Using If...Then...ElsIf... statement | Yes |
| [LineLength](LineLength.md) | Line length restriction | Yes |
| [MagicNumber](MagicNumber.md) | Using magic number | Yes |
| [MethodSize](MethodSize.md) | Method size restriction | Yes |
| [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Missing code in Raise block in "Try ... Raise ... EndTry" | Yes |
| [MissingSpace](MissingSpace.md) | Missing space | Yes |
| [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Nested constructors with parameters in structure declaration | Yes |
| [NestedStatements](NestedStatements.md) | Control flow statements should not be nested too deep | Yes |
| [NestedTernaryOperator](NestedTernaryOperator.md) | Nested ternary operator | Yes |
| [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Non export methods in API regions | Yes |
| [NumberOfOptionalParams](NumberOfOptionalParams.md) | Limit number of optional parameters in method | Yes |
| [NumberOfParams](NumberOfParams.md) | Number of method parameters restriction | Yes |
| [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Number of values in structure constructor restriction | Yes |
| [OneStatementPerLine](OneStatementPerLine.md) | One statement per line | Yes |
| [OrderOfParams](OrderOfParams.md) | Order of method parameters | Yes |
| [PairingBrokenTransaction](PairingBrokenTransaction.md) | Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" | Yes |
| [ParseError](ParseError.md) | Error parsing source code | Yes |
| [ProcedureReturnsValue](ProcedureReturnsValue.md) | Procedure must have no Return value | Yes |
| [SelfAssign](SelfAssign.md) | Variable self assignment | Yes |
| [SelfInsertion](SelfInsertion.md) | Insert a collection into itself | Yes |
| [SemicolonPresence](SemicolonPresence.md) | Statement should end with ";" | Yes |
| [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Erroneous indication of several compilation directives | Yes |
| [SpaceAtStartComment](SpaceAtStartComment.md) | Space at the beginning of the comment | Yes |
| [TernaryOperatorUsage](TernaryOperatorUsage.md) | Ternary operator usage | No |
| [TryNumber](TryNumber.md) | Cast to number in try catch block | Yes |
| [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Unknown preprocessor symbol | Yes |
| [UnreachableCode](UnreachableCode.md) | Unreachable Code | Yes |
| [UseLessForEach](UseLessForEach.md) | Useless For Each loop | Yes |
| [UsingCancelParameter](UsingCancelParameter.md) | Using "Cancel" parameter | Yes |
| [UsingFindElementByString](UsingFindElementByString.md) | Restriction on the use of "FindByDescription" and "FindByCode" methods | Yes |
| [UsingGoto](UsingGoto.md) | "Goto" usage | Yes |
| [UsingHardcodePath](UsingHardcodePath.md) | Using hardcode file paths and ip addresses in code | Yes |
| [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Storing confidential information in code | Yes |
| [UsingModalWindows](UsingModalWindows.md) | Using modal windows | No |
| [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Using of objects not available in Unix | Yes |
| [UsingServiceTag](UsingServiceTag.md) | Using service tags | Yes |
| [UsingSynchronousCalls](UsingSynchronousCalls.md) | Using synchronous calls | No |
| [UsingThisForm](UsingThisForm.md) | Using the "ThisForm" property | Yes |
| [YoLetterUsage](YoLetterUsage.md) | Using "Ё" letter in code | Yes |