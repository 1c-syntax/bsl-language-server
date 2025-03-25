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
package com.github._1c_syntax.bsl.languageserver.color;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/**
 * Данные о цветах системного перечисления @{code WebЦвета}.
 */
@AllArgsConstructor
@Getter
public enum WebColor {

  ALICE_BLUE("АкварельноСиний", "AliceBlue", 240, 248, 255),
  ANTIQUE_WHITE("АнтикБелый", "AntiqueWhite", 250, 235, 215),
  AQUA("ЦианАкварельный", "Aqua", 0, 255, 255),
  AQUAMARINE("Аквамарин", "Aquamarine", 127, 255, 212),
  AZURE("Лазурный", "Azure", 240, 255, 255),
  BEIGE("Бежевый", "Beige", 245, 245, 220),
  BISQUE("СветлоКоричневый", "Bisque", 255, 228, 196),
  BLACK("Черный", "Black", 0, 0, 0),
  BLANCHED_ALMOND("БледноМиндальный", "BlanchedAlmond", 255, 235, 205),
  BLUE("Синий", "Blue", 0, 0, 255),
  BLUE_VIOLET("СинеФиолетовый", "BlueViolet", 138, 43, 226),
  BROWN("Коричневый", "Brown", 165, 42, 42),
  BURLY_WOOD("Древесный", "BurlyWood", 222, 184, 184),
  CADET_BLUE("СероСиний", "CadetBlue", 95, 158, 160),
  CHARTREUSE("ЗеленоватоЖелтый", "Chartreuse", 127, 255, 0),
  CHOCOLATE("Шоколадный", "Chocolate", 210, 105, 30),
  CORAL("Коралловый", "Coral", 255, 127, 80),
  CORN_FLOWER_BLUE("Васильковый", "CornFlowerBlue", 100, 149, 237),
  CORN_SILK("ШелковыйОттенок", "CornSilk", 255, 248, 220),
  CREAM("Кремовый", "Cream", 255, 251, 240),
  CRIMSON("Малиновый", "Crimson", 220, 20, 60),
  CYAN("Циан", "Cyan", 0, 255, 255),
  DARK_BLUE("ТемноСиний", "DarkBlue", 0, 0, 139),
  DARK_CYAN("ЦианТемный", "DarkCyan", 0, 139, 139),
  DARK_GOLDEN_ROD("ТемноЗолотистый", "DarkGoldenRod", 184, 134, 11),
  DARK_GRAY("ТемноСерый", "DarkGray", 169, 169, 169),
  DARK_GREEN("ТемноЗеленый", "DarkGreen", 0, 100, 0),
  DARK_KHAKI("ХакиТемный", "DarkKhaki", 189, 183, 107),
  DARK_MAGENTA("ФуксинТемный", "DarkMagenta", 139, 0, 139),
  DARK_OLIVE_GREEN("ТемноОливковоЗеленый", "DarkOliveGreen", 85, 107, 47),
  DARK_ORANGE("ТемноОранжевый", "DarkOrange", 255, 140, 0),
  DARK_ORCHID("ОрхидеяТемный", "DarkOrchid", 153, 50, 204),
  DARK_RED("ТемноКрасный", "DarkRed", 139, 0, 0),
  DARK_SALMON("ЛососьТемный", "DarkSalmon", 233, 150, 122),
  DARK_SEA_GREEN("ЦветМорскойВолныТемный", "DarkSeaGreen", 143, 188, 143),
  DARK_SLATE_BLUE("ТемноГрифельноСиний", "DarkSlateBlue", 72, 61, 139),
  DARK_SLATE_GRAY("ТемноГрифельноСерый", "DarkSlateGray", 47, 79, 79),
  DARK_TURQUOISE("ТемноБирюзовый", "DarkTurquoise", 0, 206, 209),
  DARK_VIOLET("ТемноФиолетовый", "DarkViolet", 148, 0, 211),
  DEEP_PINK("НасыщенноРозовый", "DeepPink", 255, 20, 147),
  DEEP_SKY_BLUE("НасыщенноНебесноГолубой", "DeepSkyBlue", 0, 191, 255),
  DIM_GRAY("ТусклоСерый", "DimGray", 105, 105, 105),
  DODGER_BLUE("СинеСерый", "DodgerBlue", 30, 144, 255),
  FIRE_BRICK("Кирпичный", "FireBrick", 178, 34, 34),
  FLORAL_WHITE("ЦветокБелый", "FloralWhite", 255, 250, 240),
  FOREST_GREEN("ЗеленыйЛес", "ForestGreen", 34, 139, 34),
  FUCHSIA("Фуксия", "Fuchsia", 255, 0, 255),
  GAINSBORO("СеребристоСерый", "Gainsboro", 220, 220, 220),
  GHOST_WHITE("ПризрачноБелый", "GhostWhite", 248, 248, 255),
  GOLD("Золотой", "Gold", 255, 215, 0),
  GOLDENROD("Золотистый", "Goldenrod", 218, 165, 32),
  GRAY("Серый", "Gray", 128, 128, 128),
  GREEN("Зеленый", "Green", 0, 255, 0),
  GREEN_YELLOW("ЗеленоЖелтый", "GreenYellow", 173, 255, 47),
  HONEY_DEW("Роса", "HoneyDew", 240, 255, 240),
  HOT_PINK("ТеплоРозовый", "HotPink", 255, 105, 180),
  INDIAN_RED("Киноварь", "IndianRed", 205, 92, 92),
  INDIGO("Индиго", "Indigo", 75, 0, 130),
  IVORY("СлоноваяКость", "Ivory", 255, 255, 240),
  KHAKI("Хаки", "Khaki", 240, 230, 140),
  LAVENDER("БледноЛиловый", "Lavender", 230, 230, 250),
  LAVENDER_BLUSH("ГолубойСКраснымОттенком", "LavenderBlush", 255, 240, 245),
  LAWN_GREEN("ЗеленаяЛужайка", "LawnGreen", 124, 252, 0),
  LEMON_CHIFFON("Лимонный", "LemonChiffon", 255, 250, 205),
  LIGHT_BLUE("Голубой", "LightBlue", 173, 216, 230),
  LIGHT_CORAL("СветлоКоралловый", "LightCoral", 240, 128, 128),
  LIGHT_CYAN("ЦианСветлый", "LightCyan", 224, 255, 255),
  LIGHT_GOLDEN_ROD("СветлоЗолотистый", "LightGoldenRod", 238, 221, 130),
  LIGHT_GOLDEN_ROD_YELLOW("СветлоЖелтыйЗолотистый", "LightGoldenRodYellow", 250, 250, 210),
  LIGHT_GRAY("СветлоСерый", "LightGray", 211, 211, 211),
  LIGHT_GREEN("СветлоЗеленый", "LightGreen", 144, 238, 144),
  LIGHT_PINK("СветлоРозовый", "LightPink", 255, 182, 193),
  LIGHT_SALMON("ЛососьСветлый", "LightSalmon", 255, 160, 122),
  LIGHT_SEA_GREEN("ЦветМорскойВолныСветлый", "LightSeaGreen", 32, 178, 170),
  LIGHT_SKY_BLUE("СветлоНебесноГолубой", "LightSkyBlue", 135, 206, 250),
  LIGHT_SLATE_BLUE("СветлоГрифельноСиний", "LightSlateBlue", 132, 112, 255),
  LIGHT_SLATE_GRAY("СветлоГрифельноСерый", "LightSlateGray", 119, 136, 153),
  LIGHT_STEEL_BLUE("ГолубойСоСтальнымОттенком", "LightSteelBlue", 176, 196, 222),
  LIGHT_YELLOW("СветлоЖелтый", "LightYellow", 255, 255, 224),
  LIME("ЗеленоватоЛимонный", "Lime", 0, 255, 0),
  LIME_GREEN("ЛимонноЗеленый", "LimeGreen", 50, 205, 50),
  LINEN("Льняной", "Linen", 250, 240, 230),
  MAGENTA("Фуксин", "Magenta", 255, 0, 255),
  MAROON("ТемноБордовый", "Maroon", 176, 48, 96),
  MEDIUM_AQUA_MARINE("НейтральноАквамариновый", "MediumAquaMarine", 102, 205, 170),
  MEDIUM_BLUE("НейтральноСиний", "MediumBlue", 0, 0, 205),
  MEDIUM_GRAY("НейтральноСерый", "MediumGray", 160, 160, 164),
  MEDIUM_GREEN("НейтральноЗеленый", "MediumGreen", 192, 220, 192),
  MEDIUM_ORCHID("ОрхидеяНейтральный", "MediumOrchid", 186, 85, 211),
  MEDIUM_PURPLE("НейтральноПурпурный", "MediumPurple", 147, 112, 219),
  MEDIUM_SEA_GREEN("ЦветМорскойВолныНейтральный", "MediumSeaGreen", 60, 179, 113),
  MEDIUM_SLATE_BLUE("НейтральноГрифельноСиний", "MediumSlateBlue", 123, 104, 238),
  MEDIUM_SPRING_GREEN("НейтральноВесеннеЗеленый", "MediumSpringGreen", 128, 253, 205),
  MEDIUM_TURQUOISE("НейтральноБирюзовый", "MediumTurquoise", 72, 209, 204),
  MEDIUM_VIOLET_RED("НейтральноФиолетовоКрасный", "MediumVioletRed", 199, 21, 133),
  MIDNIGHT_BLUE("ПолночноСиний", "MidnightBlue", 25, 25, 112),
  MINT_CREAM("МятныйКрем", "MintCream", 245, 255, 250),
  MISTY_ROSE("ТусклоРозовый", "MistyRose", 255, 228, 225),
  MOCCASIN("ЗамшаСветлый", "Moccasin", 255, 228, 181),
  NAVAJO_WHITE("НавахоБелый", "NavajoWhite", 255, 222, 173),
  NAVY("Ультрамарин", "Navy", 0, 0, 128),
  OLD_LACE("СтароеКружево", "OldLace", 253, 245, 230),
  OLIVE("Оливковый", "Olive", 128, 128, 0),
  OLIVEDRAB("ТусклоОливковый", "Olivedrab", 107, 142, 35),
  ORANGE("Оранжевый", "Orange", 255, 165, 0),
  ORANGE_RED("ОранжевоКрасный", "OrangeRed", 255, 69, 0),
  ORCHID("Орхидея", "Orchid", 218, 112, 214),
  PALE_GOLDENROD("БледноЗолотистый", "PaleGoldenrod", 238, 232, 170),
  PALE_GREEN("БледноЗеленый", "PaleGreen", 152, 251, 152),
  PALE_TURQUOISE("БледноБирюзовый", "PaleTurquoise", 175, 238, 238),
  PALE_VIOLET_RED("БледноКрасноФиолетовый", "PaleVioletRed", 219, 112, 147),
  PAPAYA_WHIP("ТопленоеМолоко", "PapayaWhip", 255, 239, 213),
  PEACH_PUFF("Персиковый", "PeachPuff", 255, 218, 185),
  PERU("НейтральноКоричневый", "Peru", 205, 133, 63),
  PINK("Розовый", "Pink", 255, 192, 203),
  PLUM("Сливовый", "Plum", 221, 160, 221),
  POWDER_BLUE("СинийСПороховымОттенком", "PowderBlue", 176, 224, 230),
  PURPLE("Пурпурный", "Purple", 160, 32, 240),
  RED("Красный", "Red", 255, 0, 0),
  ROSY_BROWN("РозовоКоричневый", "RosyBrown", 188, 143, 143),
  ROYAL_BLUE("КоролевскиГолубой", "RoyalBlue", 65, 105, 225),
  SADDLE_BROWN("КожаноКоричневый", "SaddleBrown", 139, 69, 19),
  SALMON("Лосось", "Salmon", 250, 128, 114),
  SANDY_BROWN("ПесочноКоричневый", "SandyBrown", 244, 164, 96),
  SEAGREEN("ЦветМорскойВолны", "Seagreen", 46, 139, 87),
  SEA_SHELL("Перламутровый", "SeaShell", 255, 245, 238),
  SIENNA("Охра", "Sienna", 160, 82, 45),
  SILVER("Серебряный", "Silver", 192, 192, 192),
  SKY_BLUE("НебесноГолубой", "SkyBlue", 135, 206, 235),
  SLATE_BLUE("ГрифельноСиний", "SlateBlue", 106, 90, 205),
  SLATE_GRAY("ГрифельноСерый", "SlateGray", 112, 128, 144),
  SNOW("Белоснежный", "Snow", 255, 250, 250),
  SPRING_GREEN("ВесеннеЗеленый", "SpringGreen", 0, 255, 127),
  STEEL_BLUE("СинийСоСтальнымОттенком", "SteelBlue", 70, 130, 180),
  TAN("РыжеватоКоричневый", "Tan", 210, 180, 140),
  TEAL("ЦианНейтральный", "Teal", 0, 128, 128),
  THISTLE("БледноСиреневый", "Thistle", 216, 191, 216),
  TOMATO("Томатный", "Tomato", 255, 99, 71),
  TURQUOISE("Бирюзовый", "Turquoise", 64, 224, 208),
  VIOLET("Фиолетовый", "Violet", 238, 130, 238),
  VIOLET_RED("КрасноФиолетовый", "VioletRed", 208, 32, 144),
  WHEAT("Пшеничный", "Wheat", 245, 222, 179),
  WHITE("Белый", "White", 255, 255, 255),
  WHITE_SMOKE("ДымчатоБелый", "WhiteSmoke", 245, 245, 245),
  YELLOW("Желтый", "Yellow", 255, 255, 0),
  YELLOW_GREEN("ЖелтоЗеленый", "YellowGreen", 154, 205, 50);

  private final String ru;
  private final String en;
  private final int red;
  private final int green;
  private final int blue;

  public static Optional<WebColor> findByColor(int red, int green, int blue) {
    for (WebColor color : values()) {
      if (color.getRed() == red && color.getGreen() == green && color.getBlue() == blue) {
        return Optional.of(color);
      }
    }

    return Optional.empty();
  }
}
