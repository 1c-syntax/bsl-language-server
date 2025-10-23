/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.typo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for managing persistent cache of checked words for typo diagnostic.
 * Stores spell-checking results in memory and persists them to a JSON file.
 */
@Component
@Slf4j
public class CheckedWordsHolder {
  private static final String CACHE_FILE_NAME = ".bsl-ls-typo-cache.json";
  
  private final Map<String, Map<String, Boolean>> checkedWords = Map.of(
    "en", new ConcurrentHashMap<>(),
    "ru", new ConcurrentHashMap<>()
  );
  
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path cacheFilePath;

  public CheckedWordsHolder() {
    this.cacheFilePath = Paths.get(System.getProperty("user.home"), CACHE_FILE_NAME);
  }

  /**
   * Load cache from file on component initialization.
   */
  @PostConstruct
  public void loadCache() {
    if (!Files.exists(cacheFilePath)) {
      LOGGER.info("Cache file not found at {}, starting with empty cache", cacheFilePath);
      return;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Map<String, Boolean>> loadedCache = 
        objectMapper.readValue(cacheFilePath.toFile(), Map.class);
      
      loadedCache.forEach((lang, words) -> {
        Map<String, Boolean> langMap = checkedWords.get(lang);
        if (langMap != null) {
          langMap.putAll(words);
        }
      });
      
      LOGGER.info("Loaded typo cache from {} with {} languages", 
        cacheFilePath, loadedCache.size());
    } catch (IOException e) {
      LOGGER.error("Failed to load typo cache from {}: {}", cacheFilePath, e.getMessage());
    }
  }

  /**
   * Save cache to file on component destruction.
   */
  @PreDestroy
  public void saveCache() {
    try {
      objectMapper.writeValue(cacheFilePath.toFile(), checkedWords);
      LOGGER.info("Saved typo cache to {}", cacheFilePath);
    } catch (IOException e) {
      LOGGER.error("Failed to save typo cache to {}: {}", cacheFilePath, e.getMessage());
    }
  }

  /**
   * Check if a word has been checked for a specific language.
   *
   * @param lang language code ("en" or "ru")
   * @param word the word to check
   * @return true if the word has been checked
   */
  public boolean containsWord(String lang, String word) {
    Map<String, Boolean> langMap = checkedWords.get(lang);
    return langMap != null && langMap.containsKey(word);
  }

  /**
   * Get the status of a word (whether it has a typo) from cache.
   * Uses Spring Cache to provide additional in-memory caching.
   *
   * @param lang language code ("en" or "ru")
   * @param word the word to get status for
   * @return true if the word has a typo, false otherwise, null if not checked
   */
  @Cacheable(value = "typoCache", key = "#lang + ':' + #word")
  public Boolean getWordStatus(String lang, String word) {
    Map<String, Boolean> langMap = checkedWords.get(lang);
    if (langMap == null) {
      return null;
    }
    return langMap.get(word);
  }

  /**
   * Store the status of a word (whether it has a typo).
   * Uses Spring Cache to update the cache with the result.
   *
   * @param lang language code ("en" or "ru")
   * @param word the word to store status for
   * @param hasError true if the word has a typo, false otherwise
   * @return the stored status value
   */
  @CachePut(value = "typoCache", key = "#lang + ':' + #word")
  public Boolean putWordStatus(String lang, String word, boolean hasError) {
    Map<String, Boolean> langMap = checkedWords.get(lang);
    if (langMap != null) {
      langMap.put(word, hasError);
    }
    return hasError;
  }
}
