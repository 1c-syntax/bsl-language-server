# Diagnostics

Used for code analysis to meet coding standards and search for possible errors.

Some of diagnostics are disabled by default. Use [configuration file](../features/ConfigurationFile.md) to enable them.

To escape individual sections of code or files from triggering diagnostics, you can use special comments of the form `// BSLLS:DiagnosticKey-off` . This functionality is described in more detail in [Escaping sections of code](../features/DiagnosticIgnorance.md) .

## Implemented diagnostics

Total: **121**

* Error: **36**
* Code smell: **78**
* Vulnerability: **3**
* Security Hotspot: **4**

| Key | Name| Enabled by default | Severity | Type | Tags |
| --- | --- | :-: | --- | --- | --- |
 [BeginTransactionBeforeTryCatch](BeginTransactionBeforeTryCatch.md) | Violating transaction rules for the 'BeginTransaction' method | Yes | Major | Error | `standard` 
 [CachedPublic](CachedPublic.md) | Cached public methods | Yes | Major | Code smell | `standard`<br>`design` 
 [CanonicalSpellingKeywords](CanonicalSpellingKeywords.md) | Canonical keyword writing | Yes | Info | Code smell | `standard` 
 [CodeBlockBeforeSub](CodeBlockBeforeSub.md) | Method definitions must be placed before the module body operators | Yes | Blocker | Error | `error` 
 [CodeOutOfRegion](CodeOutOfRegion.md) | Code out of region | Yes | Info | Code smell | `standard` 
 [CognitiveComplexity](CognitiveComplexity.md) | Cognitive complexity | Yes | Critical | Code smell | `brainoverload` 
 [CommandModuleExportMethods](CommandModuleExportMethods.md) | Export methods in command and general command modules | Yes | Info | Code smell | `standard`<br>`clumsy` 
 [CommentedCode](CommentedCode.md) | Commented out code | Yes | Minor | Code smell | `standard`<br>`badpractice` 
 [CommitTransactionOutsideTryCatch](CommitTransactionOutsideTryCatch.md) | Violating transaction rules for the 'CommitTransaction' method | Yes | Major | Error | `standard` 
 [CommonModuleAssign](CommonModuleAssign.md) | CommonModuleAssign | Yes | Blocker | Error | `error` 
 [CommonModuleInvalidType](CommonModuleInvalidType.md) | Common module invalid type | Yes | Major | Error | `standard`<br>`unpredictable`<br>`design` 
 [CommonModuleNameCached](CommonModuleNameCached.md) | Missed postfix "Cached" | Yes | Major | Code smell | `standard`<br>`badpractice`<br>`unpredictable` 
 [CommonModuleNameClient](CommonModuleNameClient.md) | Missed postfix "Client" | Yes | Minor | Code smell | `standard`<br>`badpractice`<br>`unpredictable` 
 [CommonModuleNameClientServer](CommonModuleNameClientServer.md) | Missed postfix "ClientServer" | Yes | Major | Code smell | `standard`<br>`badpractice`<br>`unpredictable` 
 [CommonModuleNameFullAccess](CommonModuleNameFullAccess.md) | Missed postfix "FullAccess" | Yes | Major | Security Hotspot | `standard`<br>`badpractice`<br>`unpredictable` 
 [CommonModuleNameGlobal](CommonModuleNameGlobal.md) | Missed postfix "Global" | Yes | Major | Code smell | `standard`<br>`badpractice`<br>`brainoverload` 
 [CommonModuleNameGlobalClient](CommonModuleNameGlobalClient.md) | Global module with postfix "Client" | Yes | Major | Code smell | `standard` 
 [CommonModuleNameServerCall](CommonModuleNameServerCall.md) | Missed postfix "ServerCall" | Yes | Minor | Code smell | `standard`<br>`badpractice`<br>`unpredictable` 
 [CommonModuleNameWords](CommonModuleNameWords.md) | Unrecommended common module name | Yes | Info | Code smell | `standard` 
 [CompilationDirectiveLost](CompilationDirectiveLost.md) | Methods compilation directive | Yes | Major | Code smell | `standard`<br>`unpredictable` 
 [CompilationDirectiveNeedLess](CompilationDirectiveNeedLess.md) | Needless compilation directive | Yes | Major | Code smell | `clumsy`<br>`standard`<br>`unpredictable` 
 [ConsecutiveEmptyLines](ConsecutiveEmptyLines.md) | Consecutive empty lines | Yes | Info | Code smell | `badpractice` 
 [CreateQueryInCycle](CreateQueryInCycle.md) | Execution query on cycle | Yes | Critical | Error | `performance` 
 [CyclomaticComplexity](CyclomaticComplexity.md) | Cyclomatic complexity | Yes | Critical | Code smell | `brainoverload` 
 [DataExchangeLoading](DataExchangeLoading.md) | There is no check for the attribute DataExchange.Load in the object's event handler | Yes | Critical | Error | `standard`<br>`badpractice`<br>`unpredictable` 
 [DeletingCollectionItem](DeletingCollectionItem.md) | Deleting an item when iterating through collection using the operator "For each ... In ... Do" | Yes | Major | Error | `standard`<br>`error` 
 [DeprecatedAttributes8312](DeprecatedAttributes8312.md) | Deprecated 8.3.12 platform features. | Yes | Info | Code smell | `deprecated` 
 [DeprecatedCurrentDate](DeprecatedCurrentDate.md) | Using of the deprecated method "CurrentDate" | Yes | Major | Error | `standard`<br>`deprecated`<br>`unpredictable` 
 [DeprecatedFind](DeprecatedFind.md) | Using of the deprecated method "Find" | Yes | Minor | Code smell | `deprecated` 
 [DeprecatedMessage](DeprecatedMessage.md) | Restriction on the use of deprecated "Message" method | Yes | Minor | Code smell | `standard`<br>`deprecated` 
 [DeprecatedMethodCall](DeprecatedMethodCall.md) | Deprecated methods should not be used | Yes | Minor | Code smell | `deprecated`<br>`design` 
 [DeprecatedMethods8310](DeprecatedMethods8310.md) | Deprecated client application method. | Yes | Info | Code smell | `deprecated` 
 [DeprecatedMethods8317](DeprecatedMethods8317.md) | Using of deprecated platform 8.3.17 global methods | Yes | Info | Code smell | `deprecated` 
 [DeprecatedTypeManagedForm](DeprecatedTypeManagedForm.md) | Deprecated ManagedForm type | Yes | Info | Code smell | `standard`<br>`deprecated` 
 [DuplicateRegion](DuplicateRegion.md) | Duplicate regions | Yes | Info | Code smell | `standard` 
 [EmptyCodeBlock](EmptyCodeBlock.md) | Empty code block | Yes | Major | Code smell | `badpractice`<br>`suspicious` 
 [EmptyRegion](EmptyRegion.md) | The region should not be empty | Yes | Info | Code smell | `standard` 
 [EmptyStatement](EmptyStatement.md) | Empty statement | Yes | Info | Code smell | `badpractice` 
 [ExcessiveAutoTestCheck](ExcessiveAutoTestCheck.md) | Excessive AutoTest Check | Yes | Minor | Code smell | `standard`<br>`deprecated` 
 [ExecuteExternalCode](ExecuteExternalCode.md) | Executing of external code on the server | Yes | Critical | Vulnerability | `error`<br>`standard` 
 [ExecuteExternalCodeInCommonModule](ExecuteExternalCodeInCommonModule.md) | Executing of external code in a common module on the server | Yes | Critical | Security Hotspot | `badpractice`<br>`standard` 
 [ExportVariables](ExportVariables.md) | Ban export global module variables | Yes | Major | Code smell | `standard`<br>`design`<br>`unpredictable` 
 [ExtraCommas](ExtraCommas.md) | Commas without a parameter at the end of a method call | Yes | Major | Code smell | `standard`<br>`badpractice` 
 [FormDataToValue](FormDataToValue.md) | FormDataToValue method call | Yes | Info | Code smell | `badpractice` 
 [FunctionNameStartsWithGet](FunctionNameStartsWithGet.md) | Function name shouldn't start with "Получить" | No | Info | Code smell | `standard` 
 [FunctionOutParameter](FunctionOutParameter.md) | Out function parameter | No | Major | Code smell | `design` 
 [FunctionReturnsSamePrimitive](FunctionReturnsSamePrimitive.md) | The function always returns the same primitive value | Yes | Major | Error | `design`<br>`badpractice` 
 [FunctionShouldHaveReturn](FunctionShouldHaveReturn.md) | The function should have return | Yes | Major | Error | `suspicious`<br>`unpredictable` 
 [GetFormMethod](GetFormMethod.md) | GetForm method call | Yes | Major | Error | `error` 
 [IdenticalExpressions](IdenticalExpressions.md) | There are identical sub-expressions to the left and to the right of the "foo" operator | Yes | Major | Error | `suspicious` 
 [IfConditionComplexity](IfConditionComplexity.md) | Usage of complex expressions in the "If" condition | Yes | Minor | Code smell | `brainoverload` 
 [IfElseDuplicatedCodeBlock](IfElseDuplicatedCodeBlock.md) | Duplicated code blocks in If...Then...ElseIf... statements | Yes | Minor | Code smell | `suspicious` 
 [IfElseDuplicatedCondition](IfElseDuplicatedCondition.md) | Duplicated conditions in If...Then...ElseIf... statements | Yes | Major | Code smell | `suspicious` 
 [IfElseIfEndsWithElse](IfElseIfEndsWithElse.md) | Else...The...ElseIf... statement should end with Else branch | Yes | Major | Code smell | `badpractice` 
 [InvalidCharacterInFile](InvalidCharacterInFile.md) | Invalid character | Yes | Major | Error | `error`<br>`standard`<br>`unpredictable` 
 [IsInRoleMethod](IsInRoleMethod.md) | IsInRole global method call | Yes | Major | Code smell | `error` 
 [JoinWithSubQuery](JoinWithSubQuery.md) | Join with sub queries | Yes | Major | Code smell | `sql`<br>`standard`<br>`performance` 
 [JoinWithVirtualTable](JoinWithVirtualTable.md) | Join with virtual table | Yes | Major | Code smell | `sql`<br>`standard`<br>`performance` 
 [LatinAndCyrillicSymbolInWord](LatinAndCyrillicSymbolInWord.md) | Mixing Latin and Cyrillic characters in one identifier | Yes | Minor | Code smell | `brainoverload`<br>`suspicious` 
 [LineLength](LineLength.md) | Line Length limit | Yes | Minor | Code smell | `standard`<br>`badpractice` 
 [MagicNumber](MagicNumber.md) | Magic numbers | Yes | Minor | Code smell | `badpractice` 
 [MetadataObjectNameLength](MetadataObjectNameLength.md) | Metadata object names must not exceed the allowed length | Yes | Major | Error | `standard` 
 [MethodSize](MethodSize.md) | Method size | Yes | Major | Code smell | `badpractice` 
 [MissingCodeTryCatchEx](MissingCodeTryCatchEx.md) | Missing code in Raise block in "Try ... Raise ... EndTry" | Yes | Major | Error | `standard`<br>`badpractice` 
 [MissingSpace](MissingSpace.md) | Missing spaces to the left or right of operators + - * / = % < > <> <= >=, keywords, and also to the right of , and ; | Yes | Info | Code smell | `badpractice` 
 [MissingTemporaryFileDeletion](MissingTemporaryFileDeletion.md) | Missing temporary file deletion after using | Yes | Major | Error | `badpractice`<br>`standard` 
 [MissingVariablesDescription](MissingVariablesDescription.md) | All variables declarations must have a description | Yes | Minor | Code smell | `standard` 
 [MultilingualStringHasAllDeclaredLanguages](MultilingualStringHasAllDeclaredLanguages.md) | There is a localized text for all languages declared in the configuration | Yes | Minor | Error | `error`<br>`localize` 
 [MultilingualStringUsingWithTemplate](MultilingualStringUsingWithTemplate.md) | Partially localized text is used in the StrTemplate function | Yes | Major | Error | `error`<br>`localize` 
 [NestedConstructorsInStructureDeclaration](NestedConstructorsInStructureDeclaration.md) | Nested constructors with parameters in structure declaration | Yes | Minor | Code smell | `badpractice`<br>`brainoverload` 
 [NestedFunctionInParameters](NestedFunctionInParameters.md) | Initialization of method and constructor parameters by calling nested methods | Yes | Minor | Code smell | `standard`<br>`brainoverload`<br>`badpractice` 
 [NestedStatements](NestedStatements.md) | Control flow statements should not be nested too deep | Yes | Critical | Code smell | `badpractice`<br>`brainoverload` 
 [NestedTernaryOperator](NestedTernaryOperator.md) | Nested ternary operator | Yes | Major | Code smell | `brainoverload` 
 [NonExportMethodsInApiRegion](NonExportMethodsInApiRegion.md) | Non export methods in API regions | Yes | Major | Code smell | `standard` 
 [NonStandardRegion](NonStandardRegion.md) | Non-standard region of module | Yes | Info | Code smell | `standard` 
 [NumberOfOptionalParams](NumberOfOptionalParams.md) | Limit number of optional parameters in method | Yes | Minor | Code smell | `standard`<br>`brainoverload` 
 [NumberOfParams](NumberOfParams.md) | Number of parameters in method | Yes | Minor | Code smell | `standard`<br>`brainoverload` 
 [NumberOfValuesInStructureConstructor](NumberOfValuesInStructureConstructor.md) | Limit on the number of property values passed to the structure constructor | Yes | Minor | Code smell | `standard`<br>`brainoverload` 
 [OSUsersMethod](OSUsersMethod.md) | Using method OSUsers | Yes | Critical | Security Hotspot | `suspicious` 
 [OneStatementPerLine](OneStatementPerLine.md) | One statement per line | Yes | Minor | Code smell | `standard`<br>`design` 
 [OrderOfParams](OrderOfParams.md) | Order of Parameters in method | Yes | Major | Code smell | `standard`<br>`design` 
 [PairingBrokenTransaction](PairingBrokenTransaction.md) | Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" | Yes | Major | Error | `standard` 
 [ParseError](ParseError.md) | Source code parse error | Yes | Critical | Error | `error` 
 [ProcedureReturnsValue](ProcedureReturnsValue.md) | Procedure should not return Value | Yes | Blocker | Error | `error` 
 [PublicMethodsDescription](PublicMethodsDescription.md) | All public methods must have a description | Yes | Info | Code smell | `standard`<br>`brainoverload`<br>`badpractice` 
 [RefOveruse](RefOveruse.md) | Overuse "Reference" in a query | Yes | Major | Code smell | `sql`<br>`performance` 
 [SelfAssign](SelfAssign.md) | Variable is assigned to itself | Yes | Major | Error | `suspicious` 
 [SelfInsertion](SelfInsertion.md) | Insert a collection into itself | Yes | Major | Error | `standard`<br>`unpredictable`<br>`performance` 
 [SemicolonPresence](SemicolonPresence.md) | Statement should end with semicolon symbol ";" | Yes | Minor | Code smell | `standard`<br>`badpractice` 
 [SeveralCompilerDirectives](SeveralCompilerDirectives.md) | Erroneous indication of several compilation directives | Yes | Critical | Error | `unpredictable`<br>`error` 
 [SpaceAtStartComment](SpaceAtStartComment.md) | Space at the beginning of the comment | Yes | Info | Code smell | `standard` 
 [StyleElementConstructors](StyleElementConstructors.md) | Style element constructor | Yes | Minor | Error | `standard`<br>`badpractice` 
 [TempFilesDir](TempFilesDir.md) | TempFilesDir() method call | Yes | Major | Code smell | `standard`<br>`badpractice` 
 [TernaryOperatorUsage](TernaryOperatorUsage.md) | Ternary operator usage | No | Minor | Code smell | `brainoverload` 
 [ThisObjectAssign](ThisObjectAssign.md) | ThisObject assign | Yes | Blocker | Error | `error` 
 [TimeoutsInExternalResources](TimeoutsInExternalResources.md) | Timeouts working with external resources | Yes | Critical | Error | `unpredictable`<br>`standard` 
 [TooManyReturns](TooManyReturns.md) | Methods should not have too many return statements | No | Minor | Code smell | `brainoverload` 
 [TryNumber](TryNumber.md) | Cast to number of try catch block | Yes | Major | Code smell | `standard` 
 [Typo](Typo.md) | Typo | Yes | Info | Code smell | `badpractice` 
 [UnaryPlusInConcatenation](UnaryPlusInConcatenation.md) | Unary Plus sign in string concatenation | Yes | Blocker | Error | `suspicious`<br>`brainoverload` 
 [UnionAll](UnionAll.md) | Using keyword "UNION" in queries | Yes | Minor | Code smell | `standard`<br>`sql`<br>`performance` 
 [UnknownPreprocessorSymbol](UnknownPreprocessorSymbol.md) | Unknown preprocessor symbol | Yes | Critical | Error | `standard`<br>`error` 
 [UnreachableCode](UnreachableCode.md) | Unreachable Code | Yes | Minor | Error | `design`<br>`suspicious` 
 [UnsafeSafeModeMethodCall](UnsafeSafeModeMethodCall.md) | Unsafe SafeMode method call | Yes | Blocker | Error | `deprecated`<br>`error` 
 [UnusedLocalMethod](UnusedLocalMethod.md) | Unused local method | Yes | Major | Code smell | `standard`<br>`suspicious`<br>`unused` 
 [UnusedParameters](UnusedParameters.md) | Unused parameter | Yes | Major | Code smell | `design`<br>`unused` 
 [UseLessForEach](UseLessForEach.md) | Useless collection iteration | Yes | Critical | Error | `clumsy` 
 [UsingCancelParameter](UsingCancelParameter.md) | Using parameter "Cancel" | Yes | Major | Code smell | `standard`<br>`badpractice` 
 [UsingExternalCodeTools](UsingExternalCodeTools.md) | Using external code tools | Yes | Critical | Security Hotspot | `standard`<br>`design` 
 [UsingFindElementByString](UsingFindElementByString.md) | Using FindByName and FindByCode | Yes | Major | Code smell | `standard`<br>`badpractice`<br>`performance` 
 [UsingGoto](UsingGoto.md) | "goto" statement should not be used | Yes | Critical | Code smell | `standard`<br>`badpractice` 
 [UsingHardcodeNetworkAddress](UsingHardcodeNetworkAddress.md) | Using hardcode ip addresses in code | Yes | Critical | Vulnerability | `standard` 
 [UsingHardcodePath](UsingHardcodePath.md) | Using hardcode file paths in code | Yes | Critical | Error | `standard` 
 [UsingHardcodeSecretInformation](UsingHardcodeSecretInformation.md) | Storing confidential information in code | Yes | Critical | Vulnerability | `standard` 
 [UsingModalWindows](UsingModalWindows.md) | Using modal windows | Yes | Major | Code smell | `standard` 
 [UsingObjectNotAvailableUnix](UsingObjectNotAvailableUnix.md) | Using unavailable in Unix objects | Yes | Critical | Error | `standard`<br>`lockinos` 
 [UsingServiceTag](UsingServiceTag.md) | Using service tags | Yes | Info | Code smell | `badpractice` 
 [UsingSynchronousCalls](UsingSynchronousCalls.md) | Using synchronous calls | Yes | Major | Code smell | `standard` 
 [UsingThisForm](UsingThisForm.md) | Using deprecated property "ThisForm" | Yes | Minor | Code smell | `standard`<br>`deprecated` 
 [WrongUseOfRollbackTransactionMethod](WrongUseOfRollbackTransactionMethod.md) | Not recommended using of RollbackTransaction method | Yes | Critical | Error | `standard` 
 [YoLetterUsage](YoLetterUsage.md) | Using Russian character "yo" ("ё") in code | Yes | Info | Code smell | `standard` 