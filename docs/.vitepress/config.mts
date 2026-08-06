import { defineConfig } from 'vitepress'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { nav, sidebar } from './lib/nav'
import { diagnosticPages, directoryUrlRewrites } from './lib/pages'
import { mkdocsCompat } from './lib/mkdocs-compat'

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/**
 * Сайт публикуется на GitHub Pages по адресу `/bsl-language-server/` (и `/bsl-language-server/dev/`
 * для ветки develop), а на превью-стендах — из корня. Базовый путь задаётся переменной окружения.
 */
const rewrites = directoryUrlRewrites(docsRoot)
const nestedPages = new Set(Object.values(rewrites))

const base = process.env.DOCS_BASE ?? '/'
const hostname = process.env.DOCS_HOSTNAME ?? 'https://1c-syntax.github.io'
const siteTitle = process.env.DOCS_TITLE ?? 'BSL Language Server'

export default defineConfig({
  title: siteTitle,
  base,
  cleanUrls: false,
  metaChunk: true,
  lastUpdated: true,
  rewrites,

  head: [
    ['link', { rel: 'icon', href: `${base}logo.png` }],
    ['meta', { name: 'theme-color', content: '#f0932b' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: siteTitle }],
  ],

  sitemap: { hostname: `${hostname}${base}` },

  markdown: {
    config: (md) => mkdocsCompat(md, nestedPages),
    theme: { light: 'github-light', dark: 'github-dark' },
    lineNumbers: false,
  },

  // Ссылки на JavaDoc, бенчмарки и JSON-схемы указывают на файлы, которые кладутся рядом с
  // сайтом уже после сборки (см. workflow gh-pages), поэтому VitePress их не видит.
  ignoreDeadLinks: [/^\/javadoc/, /^\/bench/, /bench\//, /javadoc\//, /configuration\//, /^\.\.\/\.\./],

  // Каталог диагностик — широкая таблица на 186 строк; ей нужна вся ширина колонки.
  transformPageData(pageData) {
    if (/^(en\/)?diagnostics\/index\.md$/.test(pageData.relativePath)) {
      pageData.frontmatter.pageClass = 'bsl-wide'
    }
  },

  themeConfig: {
    logo: '/logo.png',
    externalLinkIcon: true,
    // Число диагностик считается по файлам документации и показывается на главной.
    diagnosticsCount: diagnosticPages(docsRoot, '').length,
    socialLinks: [
      { icon: 'github', link: 'https://github.com/1c-syntax/bsl-language-server' },
      { icon: 'telegram', link: 'https://t.me/bsl_language_server' },
    ],
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: 'Поиск', buttonAriaLabel: 'Поиск по документации' },
              modal: {
                displayDetails: 'Показать подробности',
                resetButtonTitle: 'Сбросить',
                backButtonTitle: 'Назад',
                noResultsText: 'Ничего не найдено по запросу',
                footer: {
                  selectText: 'выбрать',
                  navigateText: 'навигация',
                  closeText: 'закрыть',
                },
              },
            },
          },
        },
      },
    },
  },

  locales: {
    root: {
      label: 'Русский',
      lang: 'ru-RU',
      description:
        'Реализация Language Server Protocol для языка 1С:Предприятие 8 (BSL) и OneScript: ' +
        '186 диагностик, автодополнение, рефакторинги и пакетный анализ для CI.',
      themeConfig: {
        nav: nav(''),
        sidebar: sidebar('', docsRoot),
        outline: { level: [2, 3], label: 'На этой странице' },
        docFooter: { prev: 'Назад', next: 'Далее' },
        editLink: {
          pattern: 'https://github.com/1c-syntax/bsl-language-server/edit/develop/docs/:path',
          text: 'Предложить правку',
        },
        lastUpdated: {
          text: 'Обновлено',
          formatOptions: { dateStyle: 'medium' },
        },
        darkModeSwitchLabel: 'Оформление',
        lightModeSwitchTitle: 'Светлая тема',
        darkModeSwitchTitle: 'Тёмная тема',
        sidebarMenuLabel: 'Разделы',
        returnToTopLabel: 'Наверх',
        langMenuLabel: 'Сменить язык',
        notFound: {
          title: 'Страница не найдена',
          quote: 'Похоже, такой страницы нет. Возможно, она переехала или ссылка устарела.',
          linkLabel: 'На главную',
          linkText: 'Вернуться на главную',
        },
        footer: {
          message:
            'Распространяется по лицензии LGPL-3.0. Документация — <a href="https://github.com/1c-syntax/bsl-language-server/tree/develop/docs">исходники на GitHub</a>.',
          copyright: '© 2018–2026 1c-syntax',
        },
      },
    },

    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      description:
        'Language Server Protocol implementation for 1C:Enterprise 8 (BSL) and OneScript: ' +
        '186 diagnostics, completion, refactorings and batch analysis for CI.',
      themeConfig: {
        nav: nav('en'),
        sidebar: sidebar('en', docsRoot),
        outline: { level: [2, 3], label: 'On this page' },
        editLink: {
          pattern: 'https://github.com/1c-syntax/bsl-language-server/edit/develop/docs/:path',
          text: 'Edit this page on GitHub',
        },
        footer: {
          message:
            'Released under the LGPL-3.0 license. Docs <a href="https://github.com/1c-syntax/bsl-language-server/tree/develop/docs">sources on GitHub</a>.',
          copyright: '© 2018–2026 1c-syntax',
        },
      },
    },
  },
})
