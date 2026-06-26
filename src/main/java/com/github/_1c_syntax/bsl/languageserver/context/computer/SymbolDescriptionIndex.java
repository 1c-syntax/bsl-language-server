/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.antlr.v4.runtime.Token;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс разобранных doc-комментариев символов ({@link MethodDescription}/{@link VariableDescription})
 * <b>открытых</b> документов.
 * <p>
 * Symbol tree открытого документа перестраивается на каждый keystroke, и при сборке для каждого
 * метода/переменной жадно вызывался {@code …Description.create(...)} — полный ANTLR-разбор грамматики
 * комментария (по профилю набора текста — заметная доля стоимости перестроения), хотя текст
 * комментариев между нажатиями почти не меняется. Индекс хранит уже разобранные описания и отдаёт
 * их повторно.
 * <p>
 * Кэшируется <b>только для открытых в редакторе документов</b>: для не-открытых
 * ({@code populateContext}, пакетный анализ из CLI / sonar-bsl-community-plugin) документ
 * разбирается один раз, повторного обращения не будет — кэш бесполезен и только занимал бы память,
 * поэтому такой путь идёт мимо индекса (прямой {@code create}).
 * <p>
 * Записи привязаны к URI документа и сбрасываются на его закрытии/освобождении/удалении
 * ({@link ServerContextDocumentClosedEvent}/{@link ServerContextDocumentClearedEvent}/
 * {@link ServerContextDocumentRemovedEvent}). На изменение содержимого ({@code didChange}) индекс
 * НЕ сбрасывается — иначе терялся бы весь смысл (кэш должен пережить ребилд); устаревшие записи
 * вытесняются по размеру ({@link #PER_DOCUMENT_MAX_SIZE}) и по простою
 * ({@link #EXPIRE_AFTER_ACCESS}).
 * <p>
 * Ключ контент-адресный: сигнатуры токенов {@code line:charPositionInLine:text}. Захватывает и
 * <b>текст</b> (результат разбора), и <b>абсолютные позиции</b> (от них зависят {@code SimpleRange}
 * внутри описания, которые читают семантические токены и диагностики), поэтому попадание всегда
 * возвращает корректное и по содержимому, и по диапазонам описание. Описания неизменяемы и являются
 * чистой функцией входных токенов, поэтому переиспользование экземпляра безопасно.
 */
@Component
@WorkspaceScope
public class SymbolDescriptionIndex {

  /**
   * Потолок числа разобранных описаний на один открытый документ. Защищает от накопления
   * устаревших позиционных ключей при правках, сдвигающих номера строк, в долгой сессии.
   * С запасом покрывает число методов/переменных даже крупного модуля.
   */
  private static final int PER_DOCUMENT_MAX_SIZE = 8_192;

  /**
   * Время простоя записи, после которого она вытесняется по времени. Правки, сдвигающие номера
   * строк, плодят новое поколение позиционных ключей на каждый ребилд; на крупных модулях такой
   * кэш быстро упирается в {@link #PER_DOCUMENT_MAX_SIZE}, держа устаревшие описания. Простой в
   * минуту означает, что после паузы в наборе устаревшие ключи отваливаются, не дожидаясь
   * вытеснения по размеру.
   */
  private static final Duration EXPIRE_AFTER_ACCESS = Duration.ofMinutes(1);

  /** Разделитель ведущих и висячего комментариев в ключе (управляющий символ, в исходниках не встречается). */
  private static final String PART_SEPARATOR = String.valueOf((char) 1);

  private final Map<URI, DocumentDescriptions> descriptionsByUri = new ConcurrentHashMap<>();

  /**
   * Разобранное описание метода: из кэша открытого документа либо прямой разбор для не-открытого.
   *
   * @param documentContext документ, которому принадлежит комментарий.
   * @param comments        токены комментария над методом.
   * @return разобранное описание метода.
   */
  public MethodDescription methodDescription(DocumentContext documentContext, List<Token> comments) {
    if (!isOpen(documentContext)) {
      return MethodDescription.create(comments);
    }
    return cacheFor(documentContext.getUri()).methods
      .get(key(comments), k -> MethodDescription.create(comments));
  }

  /**
   * Разобранное описание переменной: из кэша открытого документа либо прямой разбор для не-открытого.
   *
   * @param documentContext  документ, которому принадлежит комментарий.
   * @param comments         ведущие токены комментария.
   * @param trailingComment  висячий комментарий (на той же строке), если есть.
   * @return разобранное описание переменной.
   */
  public VariableDescription variableDescription(DocumentContext documentContext,
                                                 List<Token> comments,
                                                 Optional<Token> trailingComment) {
    if (!isOpen(documentContext)) {
      return VariableDescription.create(comments, trailingComment);
    }
    var key = key(comments) + PART_SEPARATOR
      + trailingComment.map(SymbolDescriptionIndex::tokenSignature).orElse("");
    return cacheFor(documentContext.getUri()).variables
      .get(key, k -> VariableDescription.create(comments, trailingComment));
  }

  @EventListener
  public void handleDocumentClosed(ServerContextDocumentClosedEvent event) {
    descriptionsByUri.remove(event.getDocumentContext().getUri());
  }

  @EventListener
  public void handleDataCleared(ServerContextDocumentClearedEvent event) {
    descriptionsByUri.remove(event.getDocumentContext().getUri());
  }

  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    descriptionsByUri.remove(event.getUri());
  }

  private static boolean isOpen(DocumentContext documentContext) {
    return documentContext.getServerContext().isDocumentOpened(documentContext);
  }

  private DocumentDescriptions cacheFor(URI uri) {
    return descriptionsByUri.computeIfAbsent(uri, u -> new DocumentDescriptions());
  }

  private static String key(List<Token> tokens) {
    var sb = new StringBuilder();
    for (var token : tokens) {
      appendSignature(sb, token);
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String tokenSignature(Token token) {
    var sb = new StringBuilder();
    appendSignature(sb, token);
    return sb.toString();
  }

  private static void appendSignature(StringBuilder sb, Token token) {
    sb.append(token.getLine()).append(':')
      .append(token.getCharPositionInLine()).append(':')
      .append(token.getText());
  }

  /**
   * Кэши описаний одного открытого документа.
   * <p>
   * {@code executor(Runnable::run)} — обслуживание кэша (вытеснение, обработка буферов) выполняется
   * синхронно на вызывающем потоке, а не на {@link java.util.concurrent.ForkJoinPool#commonPool()}
   * по умолчанию: common pool разделяется со всей JVM и параллельной работой языкового сервера
   * (populateContext, разбор), и занимать его обслуживанием мелкого пер-документного кэша нельзя.
   * Для кэша такого размера синхронное обслуживание ничтожно по стоимости.
   */
  private static final class DocumentDescriptions {
    private final Cache<String, MethodDescription> methods =
      Caffeine.newBuilder().maximumSize(PER_DOCUMENT_MAX_SIZE)
        .expireAfterAccess(EXPIRE_AFTER_ACCESS).executor(Runnable::run).build();
    private final Cache<String, VariableDescription> variables =
      Caffeine.newBuilder().maximumSize(PER_DOCUMENT_MAX_SIZE)
        .expireAfterAccess(EXPIRE_AFTER_ACCESS).executor(Runnable::run).build();
  }
}
