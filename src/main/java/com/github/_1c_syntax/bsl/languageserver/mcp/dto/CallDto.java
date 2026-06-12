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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;

import java.util.List;

/**
 * Вызов в иерархии: связанный метод и диапазоны мест вызова.
 *
 * @param item Связанный метод/процедура (источник входящего или цель исходящего вызова).
 * @param ranges Диапазоны мест вызова в анализируемом методе.
 */
public record CallDto(CallHierarchyItemDto item, List<RangeDto> ranges) {

  public static CallDto incoming(CallHierarchyIncomingCall call) {
    return new CallDto(
      CallHierarchyItemDto.from(call.getFrom()),
      call.getFromRanges().stream().map(RangeDto::from).toList()
    );
  }

  public static CallDto outgoing(CallHierarchyOutgoingCall call) {
    return new CallDto(
      CallHierarchyItemDto.from(call.getTo()),
      call.getFromRanges().stream().map(RangeDto::from).toList()
    );
  }
}
