/*
 * This file is a part of Gherkin Language server.
 *
 * Copyright © 2020-2020
 * Valery Maximov <maximovvalery@gmail.com>, 1c-syntax team <github.com/1c-syntax> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Gherkin Language server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * Gherkin Language server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Gherkin Language server.
 */
package com.github._1c_syntax.ls_core;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Данный класс необходим spring boot'у для инжекта реализации
 */
@ComponentScan("com.github._1c_syntax.bsl.languageserver")
@Configuration
public class LSLauncherConfiguration {
}
