package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.CommentedCodeDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class QuickFixSupplierTest {

  @Test
  void testGetQuickFixClass() {
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(LanguageServerConfiguration.create());
    QuickFixSupplier quickFixSupplier = new QuickFixSupplier(diagnosticSupplier);

    Optional<Class<? extends QuickFixProvider>> quickFixClass = quickFixSupplier.getQuickFixClass("NON_EXISTING");
    assertThat(quickFixClass).isEmpty();

    quickFixClass = quickFixSupplier.getQuickFixClass("CommitTransactionOutsideTryCatch");
    assertThat(quickFixClass).isEmpty();

    quickFixClass = quickFixSupplier.getQuickFixClass("CommentedCode");
    assertThat(quickFixClass).hasValue(CommentedCodeDiagnostic.class);
  }
}