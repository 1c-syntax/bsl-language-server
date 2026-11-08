# OneScript builtin metadata

The Java server uses checked-in JSON and does not require .NET. This optional tool updates those resources from an official OneScript distribution and the matching source release.

The current resources target **OneScript 2.2.0**. The binary input is `OneScript-2.2.0-fdd-x64.zip` from the [official release](https://github.com/EvilBeaver/OneScript/releases/tag/v2.2.0), SHA-256 `60d60d901d27c26cd91dcfac86f81a37258a680b8e40df62a379e38f344f1729`.

Requirements: .NET SDK 8 and Node.js. Roslyn 4.11.0 is pinned through NuGet and the checked-in lock file; restoring the tool requires access to NuGet or a populated package cache. Obtain the matching OneScript source tree and runtime archive; extraction does not require a git checkout.

```sh
dotnet restore scripts/oscript-metadata/MetadataExporter.csproj --locked-mode --artifacts-path build/oscript-metadata
dotnet build scripts/oscript-metadata/MetadataExporter.csproj --no-restore --artifacts-path build/oscript-metadata
dotnet build/oscript-metadata/bin/MetadataExporter/debug/MetadataExporter.dll /path/to/runtime/bin build/oscript-runtime.json /path/to/onescript/source
node scripts/oscript-metadata/update.cjs build/oscript-runtime.json src/main/resources/com/github/_1c_syntax/bsl/languageserver/types/registry /path/to/onescript/source/src/OneScriptDocumenter/BasicMethods.json
```

The exporter reads attributes and signatures from the release assemblies. XML comments are parsed with the pinned Roslyn parser without compiling OneScript or running user scripts. The server's JSON schema is shared with the 1C fallback metadata: bilingual names, parameter types/defaults, unions and `variadic` tails.

`parameter-aliases.json` supplies Russian parameter labels; the English identifiers remain those declared by the runtime. Intrinsic compiler functions are identified by the matching `BasicMethods.json` and `LanguageDef.cs` token declarations; their existing descriptions and overloads are retained. Existing collection traits, deprecation annotations and primitive declarations are retained as well. Regeneration must be idempotent.

Legacy enumeration values registered with literal `WrapClrValue` or `MakeValue` calls are read from source syntax. Their constructors are never executed. The two structure constructors refine the runtime's generic `param1` label and `IValue` type using the accepted string/structure alternatives in their source bodies.

OneScript constructors capture array parameters as an argument tail even without the CLR `ParamArray` attribute; see `ScriptEngine/Machine/TypeFactory.cs` in the source release. Ordinary array-valued method parameters remain ordinary parameters.

After regeneration run the builtin-loader, global inference and signature-help tests. Review API removals and uncertain types: a CLR `IValue` alone cannot describe a more specific script union.
