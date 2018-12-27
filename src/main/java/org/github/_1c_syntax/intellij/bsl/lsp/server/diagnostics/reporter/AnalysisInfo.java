/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

import org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.FileInfo;

import java.util.Date;
import java.util.List;

public class AnalysisInfo {
  private Date date;
  private List<FileInfo> fileinfos;

  public AnalysisInfo(Date date, List<FileInfo> fileinfos) {
    this.date = date;
    this.fileinfos = fileinfos;
  }

  public Date getDate() {
    return date;
  }

  public List<FileInfo> getFileinfos() {
    return fileinfos;
  }
}
