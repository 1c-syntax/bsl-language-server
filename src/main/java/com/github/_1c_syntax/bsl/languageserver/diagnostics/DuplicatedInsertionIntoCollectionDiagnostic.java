package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.BADPRACTICE
  }

)
public class DuplicatedInsertionIntoCollectionDiagnostic extends AbstractVisitorDiagnostic {
  private final Pattern ADD_METHOD_PATTERN = CaseInsensitivePattern.compile(
    "(добавить|вставить|add|insert)");

  @Value
  private static class GroupingData {
    BSLParser.CallStatementContext callStatement;
    String identifier;
    String methodName;
    String firstParam;
    BSLParser.CallParamListContext callParamListContext;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext context) {
    final var statements = context.statement().stream()
      .map(statement -> statement.callStatement())
      .filter(Objects::nonNull)
      .filter(callStatement -> callStatement.IDENTIFIER() != null)
      .filter(callStatement -> callStatement.accessCall() != null)
      .map(callStatement -> groupingCalls(callStatement, callStatement.accessCall()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    if (statements.isEmpty()) {
      return super.visitCodeBlock(context);
    }

    final var mapOfMapsByIdentifier = statements.stream()
      .collect(Collectors.groupingBy(
        groupingData -> groupingData.getIdentifier(),
        CaseInsensitiveMap::new,
        Collectors.groupingBy(
          groupingData -> groupingData.getMethodName(),
          CaseInsensitiveMap::new,
          Collectors.groupingBy(
            groupingData -> groupingData.getFirstParam(),
            CaseInsensitiveMap::new,
            Collectors.mapping(groupingData -> groupingData, Collectors.toList()))
        )
      ));
    mapOfMapsByIdentifier.values().stream()
      .flatMap(mapByMethod -> mapByMethod.values().stream())
      .flatMap(mapByFirstParam -> mapByFirstParam.values().stream())
      .filter(listOfDuplicatedData -> listOfDuplicatedData.size() > 1)
      .forEach(listOfDuplicatedData -> fireIssue(listOfDuplicatedData));

    return super.visitCodeBlock(context);
  }

  private @Nullable
  GroupingData groupingCalls(BSLParser.CallStatementContext callStatement, BSLParser.AccessCallContext accessCallContext) {
    final var methodCallContext = accessCallContext.methodCall();
    if (methodCallContext == null) {
      return null;
    }
    final var callParamListContext = methodCallContext.doCall().callParamList();
    final var callParams = callParamListContext.callParam();
    if (callParams.isEmpty()) {
      return null;
    }
    final var methodName = methodCallContext.methodName().getText();
    if (!isAppropriateMethodCall(methodName, ADD_METHOD_PATTERN)) {
      return null;
    }
    final var fullIdentifier = callStatement.modifier().stream()
      .map(modifierContext -> modifierContext.getText())
      .reduce(callStatement.IDENTIFIER().getText(), (x, y) -> x.concat(y));

    var firstParam = callParams.get(0).getText();
    return new GroupingData(callStatement, fullIdentifier, methodName, firstParam, callParamListContext);
  }

  private boolean isAppropriateMethodCall(String methodName, Pattern pattern) {
    return pattern.matcher(methodName).matches();
  }

  private void fireIssue(List<GroupingData> listOfDuplicatedData) {
    final var mainForIssue = listOfDuplicatedData.get(1);
    final var relatedInformationList = listOfDuplicatedData.stream()
      .map(GroupingData::getCallStatement)
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(context),
        "+1"
      )).collect(Collectors.toList());
    diagnosticStorage.addDiagnostic(mainForIssue.callStatement, relatedInformationList);
  }
}
