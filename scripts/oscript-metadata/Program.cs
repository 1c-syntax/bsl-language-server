/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
using System.Text.Encodings.Web;
using System.Text.Json;

if (args.Length == 4 && args[0] == "update")
{
    MetadataUpdater.Update(args[1], args[2], args[3]);
    return;
}

if (args.Length is < 2 or > 3)
    throw new ArgumentException("Usage: MetadataExporter <OneScript bin directory> <output.json> [source directory]\n"
        + "       MetadataExporter update <runtime.json> <resource directory> <BasicMethods.json>");

var documentation = args.Length == 3 ? SourceDocumentation.Read(args[2]) : SourceDocumentation.Empty;
Console.Error.WriteLine($"Loaded {documentation.Count} source documentation entries");
var contexts = RuntimeMetadataExporter.Export(args[0], documentation, includeCompiler: args.Length == 3);
File.WriteAllText(args[1], JsonSerializer.Serialize(contexts, new JsonSerializerOptions
{
    WriteIndented = true,
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
}) + "\n");
