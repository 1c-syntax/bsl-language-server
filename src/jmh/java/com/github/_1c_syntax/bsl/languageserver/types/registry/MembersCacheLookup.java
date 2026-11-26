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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Микробенчмарк горячего пути {@link TypeRegistry#getMembers} — попадания в memo.
 * <p>
 * Мерит цену пер-типового поколения, добавленного к проверке актуальности записи:
 * {@link #realGetMembersHit} — реальный путь целиком, {@link #lookupEpochOnly} и
 * {@link #lookupEpochAndGeneration} — изолированные модели проверки «до» и «после»
 * на картах того же размера, их разница и есть накладной расход одного лишнего
 * поиска в карте поколений.
 * <p>
 * Параметр {@code invalidatedTypes} задаёт число типов, которые реально
 * инвалидировались (размер карты поколений): {@code 0} — типичный случай, когда
 * правок не было и карта пуста, {@code 440} — все типы рабочей области были
 * инвалидированы хотя бы раз (столько различных типов memo держит на cpm).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MembersCacheLookup {

  /** Столько различных типов держит memo при анализе cpm. */
  private static final int TYPE_COUNT = 440;
  private static final int MEMBERS_PER_TYPE = 20;

  private TypeRegistry typeRegistry;
  private TypeRef hotRef;

  private final AtomicLong epoch = new AtomicLong();
  private Map<Key, Cached> cache;
  private Map<Key, Long> generation;
  private Key hotKey;

  @Param({"0", "440"})
  int invalidatedTypes;

  private record Key(TypeRef ref, FileType fileType) {
  }

  private record Cached(long epoch, long generation, List<MemberDescriptor> members) {
  }

  @Setup(Level.Trial)
  public void setup() {
    typeRegistry = new TypeRegistry(List.of(), new MemberMetadataIndex());
    cache = new ConcurrentHashMap<>();
    generation = new ConcurrentHashMap<>();

    var refs = new ArrayList<TypeRef>(TYPE_COUNT);
    for (var i = 0; i < TYPE_COUNT; i++) {
      var ref = typeRegistry.intern(TypeKind.PLATFORM, "БенчТип" + i);
      refs.add(ref);
      var members = new ArrayList<MemberDescriptor>(MEMBERS_PER_TYPE);
      for (var m = 0; m < MEMBERS_PER_TYPE; m++) {
        members.add(MemberDescriptor.property("Член" + m, TypeRef.UNKNOWN, ""));
      }
      typeRegistry.registerMemberSource(ref, () -> members, FileType.BSL);
      cache.put(new Key(ref, FileType.BSL), new Cached(0L, 0L, members));
    }

    // Инвалидируем заданную долю типов, чтобы карта поколений имела реалистичный размер,
    // и снова прогреваем memo — замеряем именно попадание, а не пересборку.
    for (var i = 0; i < invalidatedTypes; i++) {
      var ref = refs.get(i);
      typeRegistry.invalidateMembers(ref);
      generation.put(new Key(ref, FileType.BSL), 1L);
    }
    for (var ref : refs) {
      typeRegistry.getMembers(ref, FileType.BSL);
    }

    hotRef = refs.get(0);
    hotKey = new Key(hotRef, FileType.BSL);
    // приводим модельную запись к тому же состоянию, что и после инвалидации
    cache.put(hotKey, new Cached(0L, generation.getOrDefault(hotKey, 0L), cache.get(hotKey).members()));
  }

  /** Реальный горячий путь: попадание в memo со сверкой эпохи и поколения. */
  @Benchmark
  @Fork(value = 2, warmups = 1)
  @Warmup(time = 2, iterations = 3)
  @Measurement(time = 2, iterations = 5)
  public void realGetMembersHit(Blackhole bh) {
    bh.consume(typeRegistry.getMembers(hotRef, FileType.BSL));
  }

  /** Модель проверки «до»: только эпоха. */
  @Benchmark
  @Fork(value = 2, warmups = 1)
  @Warmup(time = 2, iterations = 3)
  @Measurement(time = 2, iterations = 5)
  public void lookupEpochOnly(Blackhole bh) {
    var currentEpoch = epoch.get();
    var cached = cache.get(hotKey);
    if (cached != null && cached.epoch() == currentEpoch) {
      bh.consume(cached.members());
    }
  }

  /** Модель проверки «после»: эпоха плюс пер-типовое поколение. */
  @Benchmark
  @Fork(value = 2, warmups = 1)
  @Warmup(time = 2, iterations = 3)
  @Measurement(time = 2, iterations = 5)
  public void lookupEpochAndGeneration(Blackhole bh) {
    var currentEpoch = epoch.get();
    var currentGeneration = generation.getOrDefault(hotKey, 0L);
    var cached = cache.get(hotKey);
    if (cached != null && cached.epoch() == currentEpoch && cached.generation() == currentGeneration) {
      bh.consume(cached.members());
    }
  }
}
