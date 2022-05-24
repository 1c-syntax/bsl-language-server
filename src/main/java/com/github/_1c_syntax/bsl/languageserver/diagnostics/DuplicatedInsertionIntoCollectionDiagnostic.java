package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.eclipse.lsp4j.Range;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
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
  private static final List<Integer> BREAKERS_INDEXES = Arrays.asList(BSLParser.RULE_returnStatement, BSLParser.RULE_breakStatement,
    BSLParser.RULE_continueStatement, BSLParser.RULE_raiseStatement);
  private static final List<Integer> BREAKERS_ROOTS = Arrays.asList(BSLParser.RULE_subCodeBlock, BSLParser.RULE_forEachStatement,
    BSLParser.RULE_forStatement, BSLParser.RULE_whileStatement, BSLParser.RULE_tryStatement);

  private List<BSLParser.AssignmentContext> blockAssignments = null;
  private List<BSLParserRuleContext> blockBreakers = null;
  private Range blockRange = null;

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
      .forEach(listOfDuplicatedData -> excludeValidChanges(listOfDuplicatedData, codeBlock));

    blockAssignments = null;
    blockBreakers = null;
    blockRange = null;

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
      .reduce(callStatement.IDENTIFIER().getText(), (x, y) -> x.concat(".").concat(y))
      .replace("..", ".");

    var firstParam = callParams.get(0).getText();
    return new GroupingData(callStatement, fullIdentifier, methodName, firstParam, callParamListContext);
  }

  private boolean isAppropriateMethodCall(String methodName, Pattern pattern) {
    return pattern.matcher(methodName).matches();
  }

  private void excludeValidChanges(List<GroupingData> listOfDuplicatedData, BSLParser.CodeBlockContext codeBlock) {

    var listForIssue = new ArrayList<GroupingData>();
    for (int i = 0; i < listOfDuplicatedData.size(); i++) {
      if (!excludeValidElements(listOfDuplicatedData, i, codeBlock, listForIssue)){
        break;
      };
    }
    if (listForIssue.size() > 1){
      fireIssue(listForIssue);
    }
  }

  private boolean excludeValidElements(List<GroupingData> listOfDuplicatedData, int currIndex, BSLParser.CodeBlockContext codeBlock, ArrayList<GroupingData> listForIssue) {
    if (listOfDuplicatedData.size() - currIndex < 2){
      return false;
    }
    final var elem = listOfDuplicatedData.get(currIndex);
    boolean alreadyAdd = false;
    for (int i = currIndex + 1; i < listOfDuplicatedData.size(); i++) {
      if (hasValidChange(elem, listOfDuplicatedData.get(i), codeBlock)){
        break;// последующие элементы нет смысла проверять, их нужно исключать
      }
      if (!alreadyAdd){
        alreadyAdd = true;
        listForIssue.add(elem);
      }
      listForIssue.add(listOfDuplicatedData.get(i));
    }
    return true;
  }

  private boolean hasValidChange(GroupingData groupingData, GroupingData groupingData1, BSLParser.CodeBlockContext codeBlock) {
    final var range = Ranges.create(groupingData.callStatement, groupingData1.callStatement);
    if (hasAssignBetweenCalls(groupingData, range, codeBlock)){
      return true;
    }
    return hasBreakersBetweenCalls(groupingData, groupingData1, range, codeBlock);
  }

  private boolean hasAssignBetweenCalls(GroupingData groupingData, Range range, BSLParser.CodeBlockContext codeBlock) {
    return getAssignments(codeBlock).stream()
      .filter(assignmentContext -> Ranges.containsRange(range, Ranges.create(assignmentContext)))
      .anyMatch(assignmentContext -> hasValidAssign(assignmentContext, groupingData));
  }

  private boolean hasValidAssign(BSLParser.AssignmentContext assignmentContext, GroupingData groupingData) {
    final var text = assignmentContext.lValue().getText();
    return text.equalsIgnoreCase(groupingData.identifier)
      || text.equalsIgnoreCase(groupingData.firstParam);
  }

  private boolean hasBreakersBetweenCalls(GroupingData groupingData, GroupingData groupingData1, Range range, BSLParser.CodeBlockContext codeBlock) {
    return getBreakers(codeBlock).stream()
      .filter(bslParserRuleContext -> Ranges.containsRange(range, Ranges.create(bslParserRuleContext)))
      .anyMatch(breakerContext -> hasBreakerFromCodeBlock(breakerContext, codeBlock));
  }

  private boolean hasBreakerFromCodeBlock(BSLParserRuleContext breakerContext, BSLParser.CodeBlockContext codeBlock) {
    if (breakerContext.getRuleIndex() == BSLParser.RULE_returnStatement){
      return true;
    }
    final var rootParent = Trees.getRootParent(breakerContext, BREAKERS_ROOTS);
    if (rootParent.getRuleIndex() == BSLParser.RULE_subCodeBlock){
      return true;
    }
    return !Ranges.containsRange(getBlockRange(codeBlock), Ranges.create(rootParent));
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

  private List<BSLParser.AssignmentContext> getAssignments(BSLParser.CodeBlockContext codeBlock){
    if (blockAssignments != null){
      return blockAssignments;
    }
    blockAssignments = Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment).stream()
      .map(parseTree -> (BSLParser.AssignmentContext) parseTree)
      .collect(Collectors.toList());
    return blockAssignments;
  }

  private List<BSLParserRuleContext> getBreakers(BSLParser.CodeBlockContext codeBlock){
    if (blockBreakers != null){
      return blockBreakers;
    }
    blockBreakers = Trees.findAllRuleNodes(codeBlock, BREAKERS_INDEXES).stream()
      .map(parseTree -> (BSLParserRuleContext) parseTree)
      .collect(Collectors.toList());
    return blockBreakers;
  }

  private Range getBlockRange(BSLParser.CodeBlockContext codeBlock) {
    if (blockRange != null){
      return blockRange;
    }
    blockRange = Ranges.create(codeBlock);
    return blockRange;
  }
}
