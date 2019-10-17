package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnaryPlusInConcatenationDiagnosticTest extends AbstractDiagnosticTest<UnaryPlusInConcatenationDiagnostic> {
    UnaryPlusInConcatenationDiagnosticTest() {
        super(UnaryPlusInConcatenationDiagnostic.class);
    }

    @Test
    void test() {

        // when
        List<Diagnostic> diagnostics = getDiagnostics();

        //then
        assertThat(diagnostics).hasSize(2);

    }
}
