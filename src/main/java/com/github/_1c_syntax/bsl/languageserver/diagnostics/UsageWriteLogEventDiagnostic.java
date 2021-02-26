package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }

)
public class UsageWriteLogEventDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern WRITELOGEVENT = CaseInsensitivePattern.compile(
    "записьжурналарегистрации|writelogevent"
  );
  //  private static final Pattern DETAIL_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
  private static final Pattern PATTERN_DETAIL_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "подробноепредставлениеошибки|detailerrordescription"
  );
  private static final Pattern PATTERN_ERROR_INFO = CaseInsensitivePattern.compile(
    "информацияобошибке|errorinfo"
  );
  private static final Pattern PATTERN_BRIEF_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "описаниеошибки|errordescription"
  );
//  private static final String PATTERN_DETAIL_ERROR_DESCRIPTION = "подробноепредставлениеошибки|detailerrordescription";
//  private static final String PATTERN_ERROR_INFO = "информацияобошибке|errorinfo";

  private static final Pattern DETAIL_ERROR_DESCRIPTION_WITH_ERROR_INFO = CaseInsensitivePattern.compile(
    "подробноепредставлениеошибки\\s*\\(\\s*информацияобошибке\\s*\\(\\s*\\)\\s*\\)|\n" +
      "detailerrordescription\\s*\\(\\s*errorinfo\\s*\\(\\s*\\)\\s*\\)"
  );

  public UsageWriteLogEventDiagnostic() {
    super(WRITELOGEVENT);
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (!super.checkGlobalMethodCall(ctx)) {
      return false;
    }

    final var callParamListContext = ctx.doCall().callParamList();
    final var callParamContexts = callParamListContext.callParam();
    if (callParamContexts.size() < 5) {
      fireIssue(ctx, "wrongNumberMessage");
      return false;
    }

    final BSLParser.CallParamContext secondParamCtx = callParamContexts.get(1);
    if (secondParamCtx.getChildCount() == 0) {
      fireIssue(ctx, "noSecondParameter");
      return false;
    }

    final BSLParser.CallParamContext commentsCtx = callParamContexts.get(4);
    if (commentsCtx.getChildCount() == 0) {
      fireIssue(ctx, "noComment");
      return false;
    }

    if (!isRightComments(commentsCtx)) {
      fireIssue(ctx, "noDetailErrorDescription");
      return false;
    }

    return false; // todo
  }

  private void fireIssue(BSLParser.GlobalMethodCallContext ctx, String wrongNumberMessage2) {
    String wrongNumberMessage = info.getResourceString(
      wrongNumberMessage2);
    diagnosticStorage.addDiagnostic(ctx, wrongNumberMessage);
  }

  private boolean isRightComments(BSLParser.CallParamContext commentsCtx) {

    final var codeBlockContext = (BSLParser.CodeBlockContext) Trees.getRootParent(commentsCtx, BSLParser.RULE_codeBlock);
    if (haveRaiseStatement(codeBlockContext)) {
      return true;
    }
    final var globalCalls = Trees.findAllRuleNodes(commentsCtx, BSLParser.RULE_globalMethodCall);
    if (!globalCalls.isEmpty()) {
      if (isRightErrorDescriptionCall(globalCalls)) {
        return true;
      }
      if (isHaveBriefErrorDescription(globalCalls)) {  // TODO возможно, вот здесь проблемно для 17 строки
        return false;
      }
    }
    return isValidExpression(commentsCtx.expression(), codeBlockContext, true);
  }

  private boolean haveRaiseStatement(BSLParser.CodeBlockContext codeBlockContext) {
    return codeBlockContext
      .statement().stream()
      .map(statementContext -> statementContext.compoundStatement())
      .filter(Objects::nonNull)
      .map(compoundStatementContext -> compoundStatementContext.raiseStatement())
      .anyMatch(Objects::nonNull);
  }

  private static boolean isRightErrorDescriptionCall(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(ctx -> ctx instanceof BSLParser.GlobalMethodCallContext)
      .filter(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, PATTERN_DETAIL_ERROR_DESCRIPTION))
      .anyMatch(ctx -> haveFirstDescendantGlobalCall((BSLParser.GlobalMethodCallContext) ctx, PATTERN_ERROR_INFO));
  }

  private static boolean isAppropriateName(BSLParser.GlobalMethodCallContext ctx, Pattern patternDetailErrorDescription) {
    return patternDetailErrorDescription.matcher(ctx.methodName()
      .getText()).matches();
  }

  private static boolean haveFirstDescendantGlobalCall(BSLParser.GlobalMethodCallContext globalCallCtx, Pattern errorInfo) {
    final var errorInfoCall = Trees.findAllRuleNodes(globalCallCtx, BSLParser.RULE_globalMethodCall).stream()
      .filter(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, errorInfo))
      .findFirst();
    return errorInfoCall.isPresent();
  }

  private static boolean isHaveBriefErrorDescription(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(ctx -> ctx instanceof BSLParser.GlobalMethodCallContext)
      .anyMatch(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, PATTERN_BRIEF_ERROR_DESCRIPTION));
  }

  // если на одном уровне с ЗаписьЖР есть присвоение переменой из выражения-комментария,
  // то проверим, что в присвоении есть ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()
  // если есть какая-то переменная, определенная на уровень выше (например, параметр метода), то не анализируем его

  private static boolean isValidExpression(BSLParser.ExpressionContext ctx, BSLParser.CodeBlockContext codeBlock,
                                           boolean checkPrevAssignment) {
    return ctx.member().stream()
      .allMatch(memberContext -> isValidExpression(memberContext, codeBlock, checkPrevAssignment));
  }

  private static boolean isValidExpression(BSLParser.MemberContext ctx, BSLParser.CodeBlockContext codeBlock,
                                           boolean checkPrevAssignment) {
    if (ctx.constValue() != null) {
      return false;
    }
    if (checkPrevAssignment){
      final var var = ctx.complexIdentifier();
      if (var != null) {
        return isValidVarAssigment(var, codeBlock);
      }
    }
    return false;
  }

//  private static boolean isSetAsStringСonstant(BSLParser.ComplexIdentifierContext var, BSLParser.CodeBlockContext codeBlock) {
  private static boolean isValidVarAssigment(BSLParser.ComplexIdentifierContext var, BSLParser.CodeBlockContext codeBlock) {
      String varName = var.getText();
      final var assignment = getAssignment(varName, codeBlock);
      if (!assignment.isPresent()) {
        return true;
      }
      final var assignmentGlobalCalls = Trees.findAllRuleNodes(assignment.get(), BSLParser.RULE_globalMethodCall);
      if (haveAssignWithRightErrorDescription(varName, assignment, assignmentGlobalCalls)) {
        return true;
      }
      if (haveAssignWithBriefErrorDescription(varName, assignment, assignmentGlobalCalls)) {
        return false;
      }
      return assignment.map(assignmentContext -> assignmentContext.expression())
        .filter(expressionContext -> isValidExpression(expressionContext, codeBlock, false))
        .isPresent();
  }

  @NotNull
  private static Optional<BSLParser.AssignmentContext> getAssignment(String varName, BSLParser.CodeBlockContext codeBlock) {
    return Optional.of(codeBlock)
      .map(ctx -> ctx.statement())
      .filter(Objects::nonNull)
      .filter(statementContexts -> statementContexts.size() >= 1)
      .map(statementContexts -> codeBlock.statement(0))
      .map(statementContext -> statementContext.assignment())
      .filter(Objects::nonNull)
      .filter(assignmentContext -> assignmentContext.lValue().getText().equalsIgnoreCase(varName));
  }

  private static boolean haveAssignWithRightErrorDescription(String varName, Optional<BSLParser.AssignmentContext> assignment,
                                                      Collection<ParseTree> assignmentGlobalCalls) {
    final var isRight = assignment
      .filter(assignmentContext -> isRightErrorDescriptionCall(assignmentGlobalCalls))
      .isPresent();
    return isRight;
  }

  private static boolean haveAssignWithBriefErrorDescription(String varName, Optional<BSLParser.AssignmentContext> assignment,
                                                      Collection<ParseTree> assignmentGlobalCalls) {
    return assignment
      .filter(assignmentContext -> isHaveBriefErrorDescription(assignmentGlobalCalls))
      .isPresent();
  }

}
