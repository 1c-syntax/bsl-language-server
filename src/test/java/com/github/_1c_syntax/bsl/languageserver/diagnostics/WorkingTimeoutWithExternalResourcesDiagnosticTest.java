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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingTimeoutWithExternalResourcesDiagnosticTest extends AbstractDiagnosticTest<WorkingTimeoutWithExternalResourcesDiagnostic> {
	WorkingTimeoutWithExternalResourcesDiagnosticTest() {
		super(WorkingTimeoutWithExternalResourcesDiagnostic.class);
	}

	@Test
	void test() {

		// when
		List<Diagnostic> diagnostics = getDiagnostics();

		// then
		assertThat(diagnostics).hasSize(7);

		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(3, 20, 3, 75)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(5, 20, 5, 92)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(7, 18, 7, 72)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(11, 16, 11, 80)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(19, 21, 19, 65)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(32, 14, 32, 43)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(66, 10, 66, 39)))
		;

	}
}
