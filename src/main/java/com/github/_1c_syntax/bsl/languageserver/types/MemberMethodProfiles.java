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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Профили документов и директив; кеш принадлежит проверке одного файла. */
final class MemberMethodProfiles {
  private final CompilationContextResolver contextResolver;
  private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();

  MemberMethodProfiles(CompilationContextResolver resolver) {
    contextResolver = resolver;
  }

  /** Ключ профиля имеет тот же порядок, что URI и необязательная директива. */
  private record ProfileKey(URI uri, Optional<CompilerDirectiveKind> directive) implements Comparable<ProfileKey> {
    @Override
    public int compareTo(ProfileKey other) {
      var byUri = uri.compareTo(other.uri);
      return byUri == 0 ? Integer.compare(directiveOrdinal(), other.directiveOrdinal()) : byUri;
    }

    private int directiveOrdinal() {
      return directive.isPresent() ? directive.orElseThrow().ordinal() : -1;
    }
  }

  /** Повторно использовать профиль документа и директивы только в пределах проверки текущего файла. */
  Optional<MethodContext> methodContext(DocumentContext document, Optional<CompilerDirectiveKind> directive) {
    return profiles.computeIfAbsent(new ProfileKey(document.getUri(), directive),
      ignored -> contextResolver.methodContext(document, directive));
  }
}
