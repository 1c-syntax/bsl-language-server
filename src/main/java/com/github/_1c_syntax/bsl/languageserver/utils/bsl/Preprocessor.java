package com.github._1c_syntax.bsl.languageserver.utils.bsl;

import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorConstraints;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.PreprocessorSymbolNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.EnumSet;

/**
 * Набор методов для работы с препроцессором языка BSL
 */
@UtilityClass
public class Preprocessor {

  /**
   * @param ctx контекст синтаксического дерева
   * @return ограничение препроцессора
   */
  public static PreprocessorConstraints getPreprocessorConstraint(BSLParser.Preproc_symbolContext ctx) {
    PreprocessorConstraints symbol;

    switch (((TerminalNode) ctx.getChild(0)).getSymbol().getType()) {
      case BSLParser.PREPROC_ATSERVER_SYMBOL:
      case BSLParser.PREPROC_SERVER_SYMBOL:
        symbol = PreprocessorConstraints.SERVER;
        break;
      case BSLParser.PREPROC_CLIENT_SYMBOL:
      case BSLParser.PREPROC_ATCLIENT_SYMBOL:
        symbol = PreprocessorConstraints.CLIENT;
        break;
      case BSLParser.PREPROC_THINCLIENT_SYMBOL:
        symbol = PreprocessorConstraints.THIN_CLIENT;
        break;
      case BSLParser.PREPROC_MOBILECLIENT_SYMBOL:
        symbol = PreprocessorConstraints.MOBILE_CLIENT;
        break;
      case BSLParser.PREPROC_WEBCLIENT_SYMBOL:
        symbol = PreprocessorConstraints.WEB_CLIENT;
        break;
      case BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL:
        symbol = PreprocessorConstraints.EXTERNAL_CONNECTION;
        break;
      case BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL:
        symbol = PreprocessorConstraints.MANAGED_THICK_CLIENT;
        break;
      case BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL:
        symbol = PreprocessorConstraints.ORDINARY_THICK_CLIENT;
        break;
      case BSLParser.PREPROC_MOBILE_STANDALONE_SERVER:
        symbol = PreprocessorConstraints.MOBILE_STANDALONE_SERVER;
        break;
      case BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL:
        symbol = PreprocessorConstraints.MOBILE_APP_CLIENT;
        break;
      case BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL:
        symbol = PreprocessorConstraints.MOBILE_APP_SERVER;
        break;
      default:
        symbol = PreprocessorConstraints.NON_STANDARD;
        break;
    }
    return symbol;
  }

  /**
   * Вычисляет результат условия препроцессора
   *
   * @param expression условие препроцессора
   * @return результирующий набор контекстов
   */
  public static EnumSet<PreprocessorConstraints> calculatePreprocessorConstraints(BslExpression expression) {

    if (expression instanceof PreprocessorSymbolNode) {

      return getConstraintSet(((PreprocessorSymbolNode) expression).getSymbol());

    } else if (expression instanceof UnaryOperationNode && ((UnaryOperationNode) expression).getOperator() == BslOperator.NOT) {

      var subset = calculatePreprocessorConstraints(((UnaryOperationNode) expression).getOperand());
      return getInventorySet(subset);

    } else if (expression instanceof BinaryOperationNode) {

      var operation = (BinaryOperationNode) expression;

      if (operation.getOperator() == BslOperator.AND) {

        return retainSets(
          calculatePreprocessorConstraints(operation.getLeft()),
          calculatePreprocessorConstraints(operation.getRight()));

      } else if (operation.getOperator() == BslOperator.OR) {

        return joinSets(
          calculatePreprocessorConstraints(operation.getLeft()),
          calculatePreprocessorConstraints(operation.getRight()));

      }
    }

    throw new IllegalStateException();
  }

  private static EnumSet<PreprocessorConstraints> getConstraintSet(PreprocessorConstraints constraint) {
    if (constraint == PreprocessorConstraints.CLIENT) {
      return EnumSet.copyOf(PreprocessorConstraints.CLIENT_CONSTRAINTS);
    } else {
      return EnumSet.of(constraint);
    }
  }

  private static EnumSet<PreprocessorConstraints> getInventorySet(EnumSet<PreprocessorConstraints> set) {
    var resultSet = EnumSet.copyOf(PreprocessorConstraints.DEFAULT_CONSTRAINTS);
    resultSet.removeAll(set);
    return resultSet;
  }

  private static EnumSet<PreprocessorConstraints> joinSets(EnumSet<PreprocessorConstraints> firstSet, EnumSet<PreprocessorConstraints> secondSet) {
    var resultSet = firstSet.clone();
    resultSet.addAll(secondSet);
    return resultSet;
  }

  private static EnumSet<PreprocessorConstraints> retainSets(EnumSet<PreprocessorConstraints> firstSet, EnumSet<PreprocessorConstraints> secondSet) {
    var resultSet = firstSet.clone();
    resultSet.retainAll(secondSet);
    return resultSet;
  }
}
