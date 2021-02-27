package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import com.github._1c_syntax.utils.Lazy;
import org.antlr.v4.runtime.tree.ParseTree;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.BADPRACTICE
  }
)
public class MissingTempStorageDeletionDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern GET_FROM_TEMP_STORAGE_PATTERN = CaseInsensitivePattern.compile(
    "получитьизвременногохранилища|getfromtempstorage"
  );
  private static final Pattern DELETE_FROM_TEMP_STORAGE_PATTERN = CaseInsensitivePattern.compile(
    "удалитьизвременногохранилища|deletefromtempstorage"
  );

  private @Nullable BSLParser.SubContext currentSub;
  private @Nullable Lazy<List<? extends BSLParser.StatementContext> > statements;

  public MissingTempStorageDeletionDiagnostic() {
    super(GET_FROM_TEMP_STORAGE_PATTERN);
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    currentSub = null;
    statements = null;

    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    currentSub = ctx;
    statements = null;

    return super.visitSub(ctx);
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    check(ctx);
    return false;
  }

  private void check(BSLParser.GlobalMethodCallContext ctx){

    // вне процедур нет смысла проверять получение из временного хранилища, т.к. такой код не имеет смысла

    if (!super.checkGlobalMethodCall(ctx) || currentSub == null){
      return;
    }
    // чтобы не перевычислять, если в большом методе несколько вызовов ПолучитьИзВременногоХранилища
    if (statements == null){
      statements = new Lazy<>(this::calcSubStatements);
    }
    final var sourceCallContext = ctx.doCall();

    final var line = ctx.getStop().getLine();
    final var haveRightCall = statements.getOrCompute().stream()
      .filter(statement -> greaterOrEqual(statement, line))
      .anyMatch(statement -> haveDeleteFromTempStorageCall(statement, sourceCallContext));
    if (!haveRightCall){
      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private List<? extends BSLParser.StatementContext> calcSubStatements() {
    if (currentSub == null) {
      return Collections.emptyList();
    }
    final BSLParser.SubCodeBlockContext subCodeBlock;
    BSLParser.ProcedureContext method = currentSub.procedure();
    if (method == null){
      subCodeBlock = currentSub.function().subCodeBlock();
    } else {
      subCodeBlock = method.subCodeBlock();
    }
    return subCodeBlock.codeBlock().statement();
  }

  private boolean greaterOrEqual(BSLParser.StatementContext statement, int line) {
    return statement.getStart().getLine() > line;
  }

  private boolean haveDeleteFromTempStorageCall(BSLParser.StatementContext statement, BSLParser.DoCallContext sourceCallContext) {
    return Trees.findAllRuleNodes(statement, BSLParser.RULE_globalMethodCall).stream()
      .map(parseTree -> (BSLParser.GlobalMethodCallContext) parseTree)
      .filter(globalMethodCall -> DELETE_FROM_TEMP_STORAGE_PATTERN.matcher(globalMethodCall.methodName().getText())
        .matches())
      .anyMatch(globalMethodCall -> DiagnosticHelper.equalNodes(sourceCallContext, globalMethodCall.doCall()));
  }
}
