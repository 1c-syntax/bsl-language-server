package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.support.MDOType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.SQL
  }

)
public class WrongMetadataInQueryDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitDataSource(SDBLParser.DataSourceContext ctx) {
    Optional.ofNullable(ctx.table())
      .filter(this::nonMdoExists)
      .ifPresent(table -> diagnosticStorage.addDiagnostic(table,
        info.getMessage(table.getText())));

    return super.visitDataSource(ctx);
  }

  private boolean nonMdoExists(SDBLParser.TableContext table) {
    final var mdo = table.mdo();
    return mdo != null && nonMdoExists(mdo.mdoType().getText(), mdo.mdoName.getText());
  }

  private boolean nonMdoExists(String mdoType, String mdoName) {
    return getMdo(mdoType, mdoName).isEmpty();
  }

  private Optional<AbstractMDObjectBase> getMdo(String mdoTypeName, String mdoName) {
    return MDOType.fromValue(mdoTypeName).flatMap(mdoType ->
      documentContext.getServerContext().getConfiguration().getChildrenByMdoRef().entrySet().stream()
        .filter(entry -> entry.getKey().getType().equals(mdoType)
          && mdoName.equals(entry.getValue().getName()))
        .map(Map.Entry::getValue)
        .findFirst()
    );
  }
}
