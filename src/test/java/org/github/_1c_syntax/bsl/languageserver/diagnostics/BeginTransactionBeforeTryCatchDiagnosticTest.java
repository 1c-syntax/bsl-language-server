/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BeginTransactionBeforeTryCatchDiagnosticTest extends AbstractDiagnosticTest<BeginTransactionBeforeTryCatchDiagnostic> {
	BeginTransactionBeforeTryCatchDiagnosticTest() {
		super(BeginTransactionBeforeTryCatchDiagnostic.class);
	}

	@Test
	void test() {
		List<Diagnostic> diagnostics = getDiagnostics();

		assertThat(diagnostics).hasSize(7);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(29, 4, 29, 23)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(42, 8, 42, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(55, 4, 55, 23)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(68, 8, 68, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(77, 4, 77, 23)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(90, 4, 90, 23)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(102, 0, 102, 19)));
	}
}
