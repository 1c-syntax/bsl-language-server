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
