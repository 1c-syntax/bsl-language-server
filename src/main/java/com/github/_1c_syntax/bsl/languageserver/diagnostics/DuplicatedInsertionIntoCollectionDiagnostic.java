package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
  private static final Pattern ADD_METHOD_PATTERN = CaseInsensitivePattern.compile(
    "(добавить|вставить|add|insert)");
  private @Nullable  List<BSLParser.AssignmentContext> blockAssignments = null;

  @Value
  private static class GroupingData {
    BSLParser.CallStatementContext callStatement;
    String identifier;
    String methodName;
    String firstParam;
    BSLParser.CallParamListContext callParamListContext;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext codeBlock) {
    final var statements = codeBlock.statement().stream()
      .map(statement -> statement.callStatement())
      .filter(Objects::nonNull)
      .filter(callStatement -> callStatement.IDENTIFIER() != null)
      .filter(callStatement -> callStatement.accessCall() != null)
      .map(callStatement -> groupingCalls(callStatement, callStatement.accessCall()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    if (statements.isEmpty()) {
      return super.visitCodeBlock(codeBlock);
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
      .forEach(listOfDuplicatedData -> excludeNormalChanges(listOfDuplicatedData, codeBlock));

    blockAssignments = null;

    return super.visitCodeBlock(codeBlock);
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
      .reduce(callStatement.IDENTIFIER().getText(), (x, y) -> x.concat(".").concat(y));

    var firstParam = callParams.get(0).getText();
    return new GroupingData(callStatement, fullIdentifier, methodName, firstParam, callParamListContext);
  }

  private boolean isAppropriateMethodCall(String methodName, Pattern pattern) {
    return pattern.matcher(methodName).matches();
  }

  private void excludeNormalChanges(List<GroupingData> listOfDuplicatedData, BSLParser.CodeBlockContext codeBlock) {
    var listForIssue = new ArrayList<GroupingData>();
    if (blockAssignments == null){
      blockAssignments = getAssignments(codeBlock);
    }

    listForIssue.add(listOfDuplicatedData.get(0));
//    listOfDuplicatedData.subList(1, listOfDuplicatedData.size() - 1).stream()
//      .filter(groupingData -> )
    for (int i = 1; i < listOfDuplicatedData.size(); i++) {
      if (hasNormalChange(listOfDuplicatedData.get(0), listOfDuplicatedData.get(i), codeBlock)){
        break;
      }
      listForIssue.add(listOfDuplicatedData.get(i));
    }
    if (listForIssue.size() > 1){
      fireIssue(listForIssue);
    }
  }

  private boolean hasNormalChange(GroupingData groupingData, GroupingData groupingData1, BSLParser.CodeBlockContext codeBlock) {
    if (hasAssignBetweenCalls(groupingData, groupingData1)){
      return true;
    }
    return false;
  }

  private boolean hasAssignBetweenCalls(GroupingData groupingData, GroupingData groupingData1) {
    var range = Ranges.create(groupingData.callStatement, groupingData1.callStatement);
    return blockAssignments.stream()
      .filter(assignmentContext -> Ranges.containsRange(range, Ranges.create(assignmentContext)))
      .map(assignmentContext -> assignmentContext.lValue())
      .anyMatch(lValueContext -> lValueContext.getText().equalsIgnoreCase(groupingData.identifier));
  }

  private void fireIssue(List<GroupingData> listOfDuplicatedData) {
    final var dataForIssue = listOfDuplicatedData.get(1);
    final var relatedInformationList = listOfDuplicatedData.stream()
      .map(GroupingData::getCallStatement)
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(context),
        "+1"
      )).collect(Collectors.toList());
    diagnosticStorage.addDiagnostic(dataForIssue.callStatement, relatedInformationList);
  }

  private static List<BSLParser.AssignmentContext> getAssignments(BSLParser.CodeBlockContext codeBlock){
    return Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment).stream()
      .map(parseTree -> (BSLParser.AssignmentContext) parseTree)
      .collect(Collectors.toList());
  }
}
