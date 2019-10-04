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

public class UsingHardcodePathDiagnosticDiagnosticTest extends AbstractDiagnosticTest<UsingHardcodePathDiagnosticDiagnostic> {
	UsingHardcodePathDiagnosticDiagnosticTest() {
		super(UsingHardcodePathDiagnosticDiagnostic.class);
	}

	@Test
	void test() {

		List<Diagnostic> diagnostics = getDiagnostics();

		// when
		assertThat(diagnostics).hasSize(26);

		// then
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(5, 16, 5, 38)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(6, 16, 6, 50)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(7, 16, 7, 43)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(8, 16, 8, 59)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(9, 16, 9, 38)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(10, 16, 10, 50)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(11, 16, 11, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(12, 16, 12, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(13, 16, 13, 28)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(15, 16, 15, 41)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(16, 16, 16, 44)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(18, 16, 18, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(19, 16, 19, 36)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(20, 16, 20, 37)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(21, 16, 21, 38)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(31, 15, 31, 31)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(35, 23, 35, 39)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(36, 23, 36, 34)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(38, 23, 38, 64)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(39, 23, 39, 64)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(41, 44, 41, 85)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(49, 18, 49, 29)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(52, 10, 52, 27)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(53, 23, 53, 60)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(57, 15, 57, 30)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(59, 22, 59, 48)));

	}
}
