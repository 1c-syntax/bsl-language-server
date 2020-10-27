/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.BSLDocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.BSLDiagnosticInfo;
import com.github._1c_syntax.ls_core.diagnostics.CoreDiagnostic;
import org.eclipse.lsp4j.Diagnostic;

/**
 * BSLDiagnostic main purpose is to provide collection of LSP {@link Diagnostic},
 * fired on concrete {@link BSLDocumentContext}.
 * <p>
 * Each BSLDiagnostic implementation MUST contain constructor with exactly one parameter {@link BSLDiagnosticInfo}.
 * Passed BSLDiagnosticInfo MUST be stored as a object field and returned by {@link #getInfo()}.
 * <p>
 * {@link #getDiagnostics(BSLDocumentContext)} method SHOULD use {@link BSLDiagnosticStorage} to add and return diagnostics.
 */
public interface BSLDiagnostic extends CoreDiagnostic {

}
