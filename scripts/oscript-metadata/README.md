# OneScript builtin metadata

This optional .NET tool updates the server's checked-in JSON resources from an official OneScript distribution and the matching source release.

The current resources target **OneScript 2.2.0**. The binary input is `OneScript-2.2.0-fdd-x64.zip` from the [official release](https://github.com/EvilBeaver/OneScript/releases/tag/v2.2.0), SHA-256 `60d60d901d27c26cd91dcfac86f81a37258a680b8e40df62a379e38f344f1729`.

Use .NET SDK 8. Roslyn 4.11.0 is pinned through NuGet and the checked-in lock file. Restore requires access to NuGet or a populated package cache. Obtain and extract the matching OneScript source tree and runtime archive.

```sh
dotnet restore scripts/oscript-metadata/MetadataExporter.csproj --locked-mode -p:ArtifactsPath="$(pwd)/build/oscript-metadata"
dotnet build scripts/oscript-metadata/MetadataExporter.csproj --no-restore --artifacts-path build/oscript-metadata
dotnet build/oscript-metadata/bin/MetadataExporter/debug/MetadataExporter.dll /path/to/runtime/bin build/oscript-runtime.json /path/to/onescript/source
dotnet build/oscript-metadata/bin/MetadataExporter/debug/MetadataExporter.dll update build/oscript-runtime.json src/main/resources/com/github/_1c_syntax/bsl/languageserver/types/registry /path/to/onescript/source/src/OneScriptDocumenter/BasicMethods.json
```

The exporter reads attributes and signatures from the release assemblies. XML comments are parsed with the pinned Roslyn parser without compiling OneScript or running user scripts. The server's JSON schema is shared with the 1C fallback metadata: bilingual names, parameter types/defaults, unions and `variadic` tails.

Resource updates require an export containing a nonempty compiler token list from the matching source release. The updater validates this before writing either resource.

Parameter identifiers come from the runtime, and descriptions come from the matching source comments. Russian labels already recorded in the server JSON are retained for matching parameter names. A renamed parameter receives its current identifier and source description. Intrinsic compiler functions are identified by the matching `BasicMethods.json` and `LanguageDef.cs` token declarations. Their existing descriptions and overloads, collection traits, deprecation annotations and primitive declarations are retained. Regeneration must be idempotent.

Legacy enumeration values registered with literal `WrapClrValue` or `MakeValue` calls are read from source syntax. Their constructors are never executed. The two structure constructors refine the runtime's generic `param1` label and `IValue` type using the accepted string/structure alternatives in their source bodies.

OneScript constructors capture array parameters as an argument tail even without the CLR `ParamArray` attribute; see `ScriptEngine/Machine/TypeFactory.cs` in the source release. Ordinary array-valued method parameters remain ordinary parameters.

Run the C# regeneration tests with the same SDK.

```sh
dotnet restore scripts/oscript-metadata/tests/MetadataExporter.Tests.csproj --locked-mode -p:ArtifactsPath="$(pwd)/build/oscript-metadata"
dotnet test scripts/oscript-metadata/tests/MetadataExporter.Tests.csproj --no-restore --artifacts-path build/oscript-metadata
```

After regeneration run the Java builtin-loader, global inference and signature-help tests. Review API removals and uncertain types because a CLR `IValue` can represent a more specific script union.
