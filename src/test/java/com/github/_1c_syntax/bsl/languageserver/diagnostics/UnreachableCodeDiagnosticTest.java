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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UnreachableCodeDiagnosticTest extends AbstractDiagnosticTest<UnreachableCodeDiagnostic> {
	UnreachableCodeDiagnosticTest() {
		super(UnreachableCodeDiagnostic.class);
	}

	@Test
	void test() {
		List<Diagnostic> diagnostics = getDiagnostics();

		assertThat(diagnostics).hasSize(14);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(12, 12, 12, 20)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(21, 12, 21, 20)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(30, 12, 30, 20)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(37, 4, 41, 15)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(46, 4, 51, 15)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(58, 12, 58, 20)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(67, 12, 69, 21)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(82, 16, 84, 25)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(93, 8, 93, 16)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(102, 8, 102, 17)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(108, 16, 112, 26)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(134, 4, 134, 13)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(139, 4, 141, 13)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(145, 0, 145, 9)))		;
	}
}
