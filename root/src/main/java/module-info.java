module bsl.language.server {
  requires static lombok;
  requires static com.github.spotbugs.annotations;
  requires transitive org.eclipse.lsp4j;
  requires transitive v8.bsl.parser;
  requires transitive v8.bsl.mdclasses;
  requires transitive v8.bsl.utils;
  requires org.aspectj.runtime;
  requires spring.context;
  requires spring.beans;
  requires spring.boot.autoconfigure;
  requires spring.core;
  requires java.annotation;
  requires antlr4.runtime;
  requires org.apache.commons.io;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires org.apache.commons.lang3;
  requires org.eclipse.lsp4j.jsonrpc;
  requires commons.beanutils;
  requires java.desktop;
  requires spring.boot;
  requires info.picocli;
  requires org.jgrapht.core;
  requires progressbar;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires com.google.gson;
  requires jdk.unsupported;
//  requires languagetool.core;
//  requires languagetool.language;
//  requires language.en;
//  requires language.ru;
  requires com.fasterxml.jackson.dataformat.xml;
  requires java.sarif;

}