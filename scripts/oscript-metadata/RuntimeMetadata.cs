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
using System.Text.Json.Serialization;

internal sealed record MetadataName(
    [property: JsonPropertyName("Ru")] string Ru,
    [property: JsonPropertyName("En")] string En);

internal sealed record RuntimeParameter(string? Name, string ClrType, string Description, string Type,
    bool Optional, bool Variadic, string? DefaultValue);

internal sealed record RuntimeMethod(MetadataName Name, string ClrName, string[] DeprecatedNames,
    string Description, string ReturnDescription, string? Documentation, string? ReturnType,
    RuntimeParameter[] Parameters);

internal sealed record RuntimeProperty(MetadataName Name, string ClrName, string Description,
    string? Documentation, string Type, bool CanRead, bool CanWrite);

internal sealed record RuntimeContext(MetadataName Name, string? ClrName, string Description,
    string? Documentation, string? Assembly, bool Global, bool Enumeration, RuntimeMethod[] Methods,
    RuntimeProperty[] Properties, RuntimeMethod[] Constructors, MetadataName[] Values);
