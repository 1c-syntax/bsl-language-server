package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;

public enum DiagnosticCompatibilityMode {
  UNDEFINED(0, 0),
  COMPATIBILITY_MODE_8_1_0(1, 0),
  COMPATIBILITY_MODE_8_2_13(2, 13),
  COMPATIBILITY_MODE_8_2_16(2, 16),
  COMPATIBILITY_MODE_8_3_1(3, 1),
  COMPATIBILITY_MODE_8_3_2(3, 2),
  COMPATIBILITY_MODE_8_3_3(3, 3),
  COMPATIBILITY_MODE_8_3_4(3, 4),
  COMPATIBILITY_MODE_8_3_5(3, 5),
  COMPATIBILITY_MODE_8_3_6(3, 6),
  COMPATIBILITY_MODE_8_3_7(3, 7),
  COMPATIBILITY_MODE_8_3_8(3, 8),
  COMPATIBILITY_MODE_8_3_9(3, 9),
  COMPATIBILITY_MODE_8_3_10(3, 10),
  COMPATIBILITY_MODE_8_3_11(3, 11),
  COMPATIBILITY_MODE_8_3_12(3, 12),
  COMPATIBILITY_MODE_8_3_13(3, 13),
  COMPATIBILITY_MODE_8_3_14(3, 14),
  COMPATIBILITY_MODE_8_3_15(3, 15),
  COMPATIBILITY_MODE_8_3_16(3, 16);

  private CompatibilityMode compatibilityMode;

  DiagnosticCompatibilityMode(int minor, int version) {
    this.compatibilityMode = new CompatibilityMode(minor, version);
  }

  public CompatibilityMode getCompatibilityMode() {
    return compatibilityMode;
  }
}
