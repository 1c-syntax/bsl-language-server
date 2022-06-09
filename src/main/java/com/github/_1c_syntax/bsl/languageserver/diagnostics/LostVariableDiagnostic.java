package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.ERROR
  }

)

@RequiredArgsConstructor
public class LostVariableDiagnostic extends AbstractVisitorDiagnostic {
  private final ReferenceIndex referenceIndex;
  private List<VarData> variables;

    private static final Set<VariableKind> CHECKING_VARIABLE_KINDS = EnumSet.of(
//    VariableKind.MODULE,
//    VariableKind.LOCAL, // TODO учесть разные типы переменных
    VariableKind.DYNAMIC
  );
  @Value
  private static class VarData implements Comparable {
//    VariableSymbol symbol;
    String name;
    Range selectionRange;
//    Reference definition;
    List<Reference> references;
    Status status = Status.NOT_CHECKED;
//    List<List<Reference>> references;

    @Override
    public int compareTo(@NotNull Object o) {
      return compare(this.getSelectionRange(), ((VarData)o).getSelectionRange());
    }

//    public VarData(VariableSymbol symbol, List<Reference> references) {
//      this.symbol = symbol;
//      this.references = references.stream()
//        .filter(ref -> ref.getOccurrenceType() == OccurrenceType.REFERENCE)
//        .sorted((o1, o2) -> compare(o1.getSelectionRange().getStart(), o2.getSelectionRange().getStart()))
//        .collect(Collectors.toList());
//    }
  }
  private enum Status {
    NOT_CHECKED,
    ALL_REFS_INSIDE_ONE_BLOCK,
    SOME_REFS_OUTSIDE_ONE_BLOCK
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    variables = documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> CHECKING_VARIABLE_KINDS.contains(variable.getKind()))
      //.filter(variable -> !variable.isExport())
      .flatMap(variable -> getVarData(variable).stream())
//      .filter(Objects::nonNull)
      .sorted()
//      .sorted((var1, var2) -> compare(var1.getSymbol().getSelectionRange(), var2.getSymbol().getSelectionRange()))
      .collect(Collectors.toUnmodifiableList());
    if (variables.isEmpty()) {
      return defaultResult();
    }
    return super.visitFile(ctx);
  }

  @Nullable
  private List<VarData> getVarData(VariableSymbol variable) {
    List<Reference> allReferences = getSortedReferencesByLocation(variable);
    if (allReferences.isEmpty()) {
      return Collections.emptyList();
    }
    final var possibleLostReferences = hasConsecutiveDefinitions(variable, allReferences);
    return possibleLostReferences.stream()
      .map(references -> new VarData(variable.getName(), variable.getVariableNameRange(), references))
      .collect(Collectors.toUnmodifiableList());
//    if (possibleLostReferences.isEmpty()) {
//      return null;
//    }
//    return new VarData(variable.getName(), variable.getVariableNameRange(), possibleLostReferences);
  }

  private List<List<Reference>> hasConsecutiveDefinitions(VariableSymbol variable, List<Reference> allReferences) {
    List<List<Reference>> result = new ArrayList<>();
    Reference prev = null;
    if (allReferences.get(0).getOccurrenceType() == OccurrenceType.DEFINITION){
      prev = allReferences.get(0);

      final var references = new ArrayList<Reference>(1 + allReferences.size());
      references.add(Reference.of(variable.getParent().get(), variable,
        new Location(documentContext.getUri().toString(), variable.getVariableNameRange()),
          OccurrenceType.DEFINITION));
      references.addAll(allReferences);
      result.add(references);
//      return true;
    }
    for (int i = 1 ; i < allReferences.size(); i++) {
      final var next = allReferences.get(i);
      if (next.getOccurrenceType() == OccurrenceType.DEFINITION){
        if (prev != null) {
          final var references = new ArrayList<Reference>(2 + allReferences.size() - i);
          references.add(prev);
          references.add(next);
          if (i < allReferences.size() - 1){
            references.addAll(allReferences.subList(i + 1, allReferences.size()));
          }
          result.add(references);
//          return true;
        }
        prev = next;
        continue;
      }
      prev = null;
    }
    return result;
  }

  @NotNull
  private List<Reference> getSortedReferencesByLocation(VariableSymbol variable) {
    final var references = referenceIndex.getReferencesTo(variable);
//    if (references.stream()
//        .noneMatch(ref -> ref.getOccurrenceType() == OccurrenceType.REFERENCE)){
//      return null;
//    }
    return references.stream()
      .sorted((o1, o2) -> compare(o1.getSelectionRange(), o2.getSelectionRange()))
      .collect(Collectors.toList());
  }

  @Override
  public ParseTree visitSubCodeBlock(BSLParser.SubCodeBlockContext ctx) {
    final var blockRange = Ranges.create(ctx);
    variables.stream()
      //.filter(varData -> Ranges.containsRange(blockRange, varData.getSymbol().getSelectionRange()))
//      .flatMap(varData -> isLostVars(blockRange, varData).stream())
      .filter(varData -> isLostVars(blockRange, varData))
      .forEach(varData -> fireIssue(varData));
//      .collect(Collectors.toList());

//    variables.stream()
//      .filter(varData -> Ranges.containsRange(blockRange, varData.symbol.getRange()))
//      .sorted((o1, o2) -> {
//        final var range1 = o1.getSymbol().getRange().getStart();
//        final var range2 = o2.getSymbol().getRange().getStart();
//        return compare(range1, range2);
//      })
//      .collect(Collectors.toList());
//    variables.stream()
//      .filter(varData -> Ranges.containsRange(blockRange, varData.symbol.getRange()))
//      .
    return super.visitSubCodeBlock(ctx);
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    final var parseTree = super.visitCodeBlock(ctx);
    return parseTree;
  }

  private boolean isLostVars(Range blockRange, VarData varData) {
    return varData.references
      // TODO избавиться от 1го элемента, т.к. он дублируется в соседних полях VarData
          .subList(1, varData.references.size()).stream()
            .allMatch(reference -> Ranges.containsRange(blockRange, reference.getSelectionRange()));
//      .map(references -> new VarData(varData.getName(), varData.getDefinition(), references))
//      .collect(Collectors.toUnmodifiableList());
//    return false;
//    final var isVarDefIntoCodeBlock = Ranges.containsRange(blockRange, varData.getSymbol().getSelectionRange());
//    final var references = varData.references;
////    if (references.get(0).getOccurrenceType() == OccurrenceType.DEFINITION
////      && ){
////
////    }
//    for (int i = 0; i < references.size(); i++) {
//      final var reference = references.get(i);
//      if (reference.getOccurrenceType() != OccurrenceType.DEFINITION){
//        continue;
//      }
//      if (i == references.size() - 1){
//        return true;
//      }
//      //if (isAllNextRefsIntoCodeBlock(blockRange, reference, references.subList(i + 1, references.size() - 1)))
//    }
//    return false;
////    return varData.getReferences()
////      .stream()
////      .allMatch(reference -> Ranges.containsRange(blockRange, reference.getSelectionRange()));
  }

  private static int compare(Range o1, Range o2) {
    return compare(o1.getStart(), o2.getStart());
  }

  public static int compare(Position pos1, Position pos2) {
    // 1,1 10,10
    if (pos1.getLine() < pos2.getLine()) {
      return -1;
    }
    // 10,10 1,1
    if (pos1.getLine() > pos2.getLine()) {
      return 1;
    }
    // 1,4 1,9
    if (pos1.getCharacter() < pos2.getCharacter()) {
      return -1;
    }
    // 1,9 1,4
    if (pos1.getCharacter() > pos2.getCharacter()) {
      return 1;
    }
    return 0;
  }

  private void fireIssue(VarData varData) {
    final var relatedInformationList = varData.getReferences().stream()
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        context.getSelectionRange(),
        "+1"
      )).collect(Collectors.toList());
    final var message = info.getMessage(varData.getName());
    diagnosticStorage.addDiagnostic(varData.getSelectionRange(), message, relatedInformationList);
  }}
