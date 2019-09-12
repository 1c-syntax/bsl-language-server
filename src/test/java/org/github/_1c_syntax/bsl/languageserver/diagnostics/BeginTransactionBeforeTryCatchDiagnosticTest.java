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
