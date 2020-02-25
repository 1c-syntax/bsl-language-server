package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;

import java.util.List;
import java.util.Optional;

public final class Regions {

  public static Optional<RegionSymbol> getRootRegion(List<RegionSymbol> regions, RegionSymbol currentRegion) {
    return regions.stream()
      .filter(regionSymbol -> findRecursivelyRegion(regionSymbol, currentRegion))
      .findFirst();
  }

  private static boolean findRecursivelyRegion(RegionSymbol parent, RegionSymbol toFind) {
    if (parent.equals(toFind)) {
      return true;
    }

    return parent.getChildren().stream().anyMatch(regionSymbol -> findRecursivelyRegion(regionSymbol, (toFind)));
  }

}
