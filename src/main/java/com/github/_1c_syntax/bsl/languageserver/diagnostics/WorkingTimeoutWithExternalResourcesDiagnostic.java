/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
	type = DiagnosticType.ERROR,
	severity = DiagnosticSeverity.CRITICAL,
	minutesToFix = 5
)
public class WorkingTimeoutWithExternalResourcesDiagnostic extends AbstractVisitorDiagnostic {

	private static final Pattern patternNewExpression = Pattern.compile(
		"^(FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection|WSОпределения|WSDefinitions|" +
			"WSПрокси|WSProxy|ИнтернетПочтовыйПрофиль|InternetMailProfile)",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);


	public static final Pattern patternTimeout = Pattern.compile("^.(Таймаут|Timeout)",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final int DEFAULT_NUMBER_TIMEOUT = 5;

	private boolean checkTimeoutIntoParamList(BSLParser.NewExpressionContext newExpression, AtomicBoolean isContact) {
		boolean needContinue = true;
		int numberTimeout = DEFAULT_NUMBER_TIMEOUT;
		if (isWSDefinitions(newExpression)) {
			numberTimeout = DEFAULT_NUMBER_TIMEOUT - 1;
		}

		BSLParser.DoCallContext doCallContext = newExpression.doCall();
		if (doCallContext == null) {
			return true;
		}

		List<BSLParser.CallParamContext> listParams = doCallContext.callParamList().callParam();
		if (listParams == null) {
			return true;
		}

		if (listParams.size() > numberTimeout) {
			BSLParser.CallParamContext param = listParams.get(numberTimeout);
			BSLParser.ExpressionContext expression = param.expression();
			if (expression == null) {
				return true;
			}

			if (expression.member().isEmpty()) {
				return true;
			}

			BSLParser.MemberContext memberContext = expression.member().get(0);
			if (isNumberOrVariable(memberContext)) {
				needContinue = false;
				isContact.set(false);
			}

		}

		return needContinue;
	}

	@Override
	public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
		Collection<ParseTree> list = Trees.findAllRuleNodes(ctx, BSLParser.RULE_newExpression);
		list.forEach(
			e -> {
				AtomicBoolean isContact = new AtomicBoolean(true);
				BSLParser.NewExpressionContext newExpression = (BSLParser.NewExpressionContext) e;
				boolean needContinue = true;
				if (isSpecificTypeName(newExpression)) {
					needContinue = checkTimeoutIntoParamList(newExpression, isContact);
					if (needContinue) {
						BSLParser.StatementContext statementContext = (BSLParser.StatementContext)
							Trees.getAncestorByRuleIndex((ParserRuleContext) e, BSLParser.RULE_statement);
						String variableName = getVariableName(statementContext);
						int filterLine = newExpression.getStart().getLine();
						Collection<ParseTree> listNextStatements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement)
							.stream()
							.filter(node -> ((BSLParser.StatementContext) node).getStart().getLine() > filterLine)
							.collect(Collectors.toList());
						checkNextStatement(listNextStatements, variableName, isContact);
					}
					if (isContact.get()) {
						diagnosticStorage.addDiagnostic(newExpression, getDiagnosticMessage());
					}
				}
			}
		);
		return super.visitCodeBlock(ctx);
	}

	private void checkNextStatement(Collection<ParseTree> listNextStatments, String variableName, AtomicBoolean isContact) {
		listNextStatments.forEach(element -> {
			BSLParser.StatementContext localStatement = (BSLParser.StatementContext) element;
			String thisVariableName = getVariableName(localStatement);
			if (thisVariableName.equalsIgnoreCase(variableName)
				&& isTimeoutModifer(localStatement)
				&& isNumberOrVariable(localStatement.assignment().expression().member(0))) {

				isContact.set(false);

			}
		});
	}

	private boolean isTimeoutModifer(BSLParser.StatementContext localStatement) {
		BSLParser.ComplexIdentifierContext complexIdentifier = localStatement.assignment().complexIdentifier();
		if (complexIdentifier.isEmpty()) {
			return false;
		}
		List<BSLParser.ModifierContext> listModifer = complexIdentifier.modifier();
		if (listModifer.isEmpty()) {
			return false;
		}
		BSLParser.ModifierContext modifier = listModifer.get(0);
		Matcher matcher = patternTimeout.matcher(modifier.getText());
		return matcher.find();
	}

	private String getVariableName(BSLParser.StatementContext statement) {
		String variableName = "";
		if (statement.assignment() != null){
			BSLParser.ComplexIdentifierContext complexIdentifierContext = statement.assignment().complexIdentifier();
			if (complexIdentifierContext != null) {
				variableName = complexIdentifierContext.getStart().getText();
			}
		}
		return variableName;
	}

	private boolean isSpecificTypeName(BSLParser.NewExpressionContext newExpression) {
		boolean result = false;
		BSLParser.TypeNameContext typeNameContext = newExpression.typeName();
		if (typeNameContext != null) {
			Matcher matcherTypeName = patternNewExpression.matcher(typeNameContext.getText());
			if (matcherTypeName.find()) {
				result = true;
			}
		}
		return result;
	}

	private boolean isWSDefinitions(BSLParser.NewExpressionContext newExpression) {
		return newExpression.typeName() != null && DiagnosticHelper.isWSDefinitionsType(newExpression.typeName());
	}

	private boolean isNumberOrVariable(BSLParser.MemberContext member) {
		boolean result = false;
		if (member.constValue() != null) {
			if (member.constValue().numeric() != null) {
				result = true;
			}
		} else {
			if (member.complexIdentifier() != null) {
				result = true;
			}
		}
		return result;
	}
}
