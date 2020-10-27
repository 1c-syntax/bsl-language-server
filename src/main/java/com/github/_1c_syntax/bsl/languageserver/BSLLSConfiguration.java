package com.github._1c_syntax.bsl.languageserver;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Данный класс необходим spring boot'у для инжекта реализации ls-core
 */
@SpringBootApplication
@ComponentScan("com.github._1c_syntax.ls_core")
public class BSLLSConfiguration {
}
