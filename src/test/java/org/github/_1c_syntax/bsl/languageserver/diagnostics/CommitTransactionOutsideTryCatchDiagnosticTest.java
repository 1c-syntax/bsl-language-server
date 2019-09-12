package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CommitTransactionOutsideTryCatchDiagnosticTest extends AbstractDiagnosticTest<CommitTransactionOutsideTryCatchDiagnostic> {
	CommitTransactionOutsideTryCatchDiagnosticTest() {
		super(CommitTransactionOutsideTryCatchDiagnostic.class);
	}

	@Test
	void test() {
		List<Diagnostic> diagnostics = getDiagnostics();

		assertThat(diagnostics).hasSize(8);
		assertThat(diagnostics)
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(36, 4, 36, 30)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(45, 12, 45, 38)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(57, 8, 57, 34)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(66, 4, 66, 30)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(74, 8, 74, 34)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(86, 8, 86, 34)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(98, 8, 98, 34)))
			.anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(106, 0, 106, 26)));
	}
}
