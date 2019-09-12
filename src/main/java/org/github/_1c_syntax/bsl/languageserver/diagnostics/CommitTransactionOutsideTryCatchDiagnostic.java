package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
	type = DiagnosticType.ERROR,
	severity = DiagnosticSeverity.MAJOR,
	minutesToFix = 10
)
public class CommitTransactionOutsideTryCatchDiagnostic extends AbstractVisitorDiagnostic {

	private Pattern endTransaction = Pattern.compile(
		"ЗафиксироватьТранзакцию|CommitTransaction",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private BSLParserRuleContext nodeEndTransaction = null;
	private BSLParserRuleContext nodeEndFile = null;

	@Override
	public ParseTree visitTryStatement(BSLParser.TryStatementContext ctx) {

		if(nodeEndTransaction != null) {
			diagnosticStorage.addDiagnostic(nodeEndTransaction);
		}

		nodeEndTransaction = null;
		return super.visitTryStatement(ctx);
	}

	@Override
	public ParseTree visitExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
		nodeEndTransaction = null;
		return super.visitExceptCodeBlock(ctx);
	}

	@Override
	public ParseTree visitStatement(BSLParser.StatementContext ctx) {
		int ctxType = ctx.getStart().getType();

		if(ctxType == BSLParser.TRY_KEYWORD || ctxType == BSLParser.EXCEPT_KEYWORD) {
			return super.visitStatement(ctx);
		}

		// Это код после ЗафиксироватьТранзакцию
		if(nodeEndTransaction != null) {
			diagnosticStorage.addDiagnostic(nodeEndTransaction);
			nodeEndTransaction = null;
		}

		// Ищем только в идентификаторах
		if(ctxType == BSLParser.IDENTIFIER && endTransaction.matcher(ctx.getText()).find()) {
			nodeEndTransaction = ctx;
		}

		// Если это код в конце модуля, ЗафиксироватьТранзакию был/есть тогда фиксируем
		if(nodeEndFile != null && nodeEndTransaction != null && nodeEndFile == ctx) {
			diagnosticStorage.addDiagnostic(nodeEndTransaction);
			nodeEndTransaction = null;
		}
		return super.visitStatement(ctx);
	}

	@Override
	public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
		// Находим последний стейт в модуле и запоминаем его
		List<ParseTree> statements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement).stream().collect(Collectors.toList());
		if(statements.size() > 0) {
			nodeEndFile = (BSLParserRuleContext) statements.get(statements.size() - 1);
		}
		return super.visitFileCodeBlock(ctx);
	}
}
