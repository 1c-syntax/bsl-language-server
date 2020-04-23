package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_12,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.DEPRECATED
  }

)
public class DeprecatedAttributes8312Diagnostic extends AbstractVisitorDiagnostic {

  private static final String CHART_PLOT_AREA_RU = "ОбластьПостроенияДиаграммы";
  private static final String CHART_PLOT_AREA_EN = "ChartPlotArea";
  // ChartPlotArea deprecated attributes
  private static final String SHOW_SCALE_RU = "ОтображатьШкалу";
  private static final String SHOW_SCALE_EN = "ShowScale";
  private static final String SCALE_LINES_RU = "ЛинииШкалы";
  private static final String SCALE_LINES_EN = "ScaleLines";
  private static final String SCALE_COLOR_RU = "ЦветШкалы";
  private static final String SCALE_COLOR_EN = "ScaleColor";
  private static final String SHOW_SERIES_SCALE_LABELS_RU = "ОтображатьПодписиШкалыСерий";
  private static final String SHOW_SERIES_SCALE_LABELS_EN = "ShowSeriesScaleLabels";
  private static final String SHOW_POINTS_SCALE_LABELS_RU = "ОтображатьПодписиШкалыТочек";
  private static final String SHOW_POINTS_SCALE_LABELS_EN = "ShowPointsScaleLabels";
  private static final String SHOW_VALUES_SCALE_LABELS_RU = "ОтображатьПодписиШкалыЗначений";
  private static final String SHOW_VALUES_SCALE_LABELS_EN = "ShowValuesScaleLabels";
  private static final String SHOW_SCALE_VALUE_LINES_RU = "ОтображатьЛинииЗначенийШкалы";
  private static final String SHOW_SCALE_VALUE_LINES_EN = "ShowScaleValueLines";
  private static final String VALUE_SCALE_FORMAT_RU = "ФорматШкалыЗначений";
  private static final String VALUE_SCALE_FORMAT_EN = "ValueScaleFormat";
  private static final String LABELS_ORIENTATION_RU = "ОриентацияМеток";
  private static final String LABELS_ORIENTATION_EN = "LabelsOrientation";

  private static final String CHART_RU = "Диаграмма";
  private static final String CHART_EN = "Chart";
  private static final String GANTT_CHART_EN = "GanttChart";
  private static final String GANTT_CHART_RU = "ДиаграммаГанта";
  private static final String PIVOT_CHART_EN = "PivotChart";
  private static final String PIVOT_CHART_RU = "СводнаяДиаграмма";
  // General deprecated attributes
  private static final String SHOW_LEGEND_EN = "ShowLegend";
  private static final String SHOW_LEGEND_RU = "ОтображатьЛегенду";
  private static final String SHOW_TITLE_EN = "ShowTitle";
  private static final String SHOW_TITLE_RU = "ОтображатьЗаголовок";
  // Chart deprecated attributes
  private static final String COLOR_PALETTE_EN = "ColorPalette";
  private static final String COLOR_PALETTE_RU = "ПалитраЦветов";
  private static final String GRADIENT_PALETTE_START_COLOR_EN = "GradientPaletteStartColor";
  private static final String GRADIENT_PALETTE_START_COLOR_RU = "ЦветНачалаГрадиентнойПалитры";
  private static final String GRADIENT_PALETTE_END_COLOR_EN = "GradientPaletteEndColor";
  private static final String GRADIENT_PALETTE_END_COLOR_RU = "ЦветКонцаГрадиентнойПалитры";
  private static final String GRADIENT_PALETTE_MAX_COLORS_EN = "GradientPaletteMaxColors";
  private static final String GRADIENT_PALETTE_MAX_COLORS_RU = "МаксимальноеКоличествоЦветовГрадиентнойПалитры";
  // Chart deprecated methods
  private static final String GET_PALETTE_EN = "GetPalette";
  private static final String GET_PALETTE_RU = "ПалитраЦветов";
  private static final String SET_PALETTE_EN = "SetPalette";
  private static final String SET_PALETTE_RU = "УстановитьПалитру";


  private static String getDeprecatedAttributesRegex(Metaobject metaobject) {
    HashMap<String, String> attributesPair = new HashMap<>();
    if (metaobject.equals(Metaobject.CHART_PLOT_AREA)) {
      attributesPair.put(SHOW_SCALE_RU, SHOW_SCALE_EN);
      attributesPair.put(SCALE_LINES_RU, SCALE_LINES_EN);
      attributesPair.put(SCALE_COLOR_RU, SCALE_COLOR_EN);
      attributesPair.put(SHOW_SERIES_SCALE_LABELS_RU, SHOW_SERIES_SCALE_LABELS_EN);
      attributesPair.put(SHOW_POINTS_SCALE_LABELS_RU, SHOW_POINTS_SCALE_LABELS_EN);
      attributesPair.put(SHOW_VALUES_SCALE_LABELS_RU, SHOW_VALUES_SCALE_LABELS_EN);
      attributesPair.put(SHOW_SCALE_VALUE_LINES_RU, SHOW_SCALE_VALUE_LINES_EN);
      attributesPair.put(VALUE_SCALE_FORMAT_RU, VALUE_SCALE_FORMAT_EN);
      attributesPair.put(LABELS_ORIENTATION_RU, LABELS_ORIENTATION_EN);
    } else if (metaobject.equals(Metaobject.CHART)) {
      attributesPair.put(SHOW_LEGEND_EN, SHOW_LEGEND_RU);
      attributesPair.put(SHOW_TITLE_EN, SHOW_TITLE_RU);
      attributesPair.put(COLOR_PALETTE_EN, COLOR_PALETTE_RU);
      attributesPair.put(GRADIENT_PALETTE_START_COLOR_EN, GRADIENT_PALETTE_START_COLOR_RU);
      attributesPair.put(GRADIENT_PALETTE_END_COLOR_EN, GRADIENT_PALETTE_END_COLOR_RU);
      attributesPair.put(GRADIENT_PALETTE_MAX_COLORS_EN, GRADIENT_PALETTE_MAX_COLORS_RU);
    }
    StringJoiner regex = new StringJoiner("|");

    attributesPair.forEach((k, v) -> {
      regex.add(k);
      regex.add(v);
    });

    return regex.toString();
  }

  private static String getChartMethodsRegex() {
    HashMap<String, String> attributesPair = new HashMap<>();

    attributesPair.put(GET_PALETTE_EN, GET_PALETTE_RU);
    attributesPair.put(SET_PALETTE_EN, SET_PALETTE_RU);

    StringJoiner regex = new StringJoiner("|");

    attributesPair.forEach((k, v) -> {
      regex.add(k);
      regex.add(v);
    });

    return regex.toString();
  }

  private static String getMetaobjectNameRegex(Metaobject metaobject) {
    HashMap<String, String> namePair = new HashMap<>();
    if (metaobject.equals(Metaobject.CHART_PLOT_AREA)) {
      namePair.put(CHART_PLOT_AREA_RU, CHART_PLOT_AREA_EN);
    } else if (metaobject.equals(Metaobject.CHART)) {
      namePair.put(CHART_RU, CHART_EN);
      namePair.put(GANTT_CHART_EN, GANTT_CHART_RU);
      namePair.put(PIVOT_CHART_EN, PIVOT_CHART_RU);
    }
    StringJoiner regex = new StringJoiner("|");

    namePair.forEach((k, v) -> {
      regex.add(k);
      regex.add(v);
    });

    return regex.toString();
  }

  private static final Pattern CHART_PLOT_AREA_ATTRIBUTES_PATTERN = Pattern.compile(
    getDeprecatedAttributesRegex(Metaobject.CHART_PLOT_AREA),
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CHART_ATTRIBUTES_PATTERN = Pattern.compile(
    getDeprecatedAttributesRegex(Metaobject.CHART),
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CHART_METHODS_PATTERN = Pattern.compile(
    getChartMethodsRegex(),
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CHART_PLOT_AREA_NAME_PATTERN = Pattern.compile(
    getMetaobjectNameRegex(Metaobject.CHART_PLOT_AREA),
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CHART_NAME_PATTERN = Pattern.compile(
    getMetaobjectNameRegex(Metaobject.CHART),
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);


  public DeprecatedAttributes8312Diagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitAccessProperty(BSLParser.AccessPropertyContext ctx) {

    if (isDeprecated(ctx, CHART_PLOT_AREA_NAME_PATTERN, CHART_PLOT_AREA_ATTRIBUTES_PATTERN)
    || isDeprecated(ctx, CHART_NAME_PATTERN, CHART_ATTRIBUTES_PATTERN)) {
      diagnosticStorage.addDiagnostic(ctx);
    }

    return super.visitAccessProperty(ctx);
  }

  private boolean isDeprecated(BSLParser.AccessPropertyContext ctx,
                               Pattern objectNamePattern,
                               Pattern deprecatedAttributesPattern) {

    Matcher matcherChartPlotArea = deprecatedAttributesPattern.matcher(ctx.getText().substring(1));

    if (matcherChartPlotArea.matches()) {
      var complexCtx = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_complexIdentifier);
      if (complexCtx == null) {
        complexCtx = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_lValue);
        if (complexCtx == null) {
          return false;
        }
      }

      Matcher matcherChartPlotAreaName = objectNamePattern.matcher(
        complexCtx.getStart().getText()
      );
      return matcherChartPlotAreaName.matches();
    }

    return false;
  }

  private enum Metaobject{
    CHART,
    CHART_PLOT_AREA
  }
}


