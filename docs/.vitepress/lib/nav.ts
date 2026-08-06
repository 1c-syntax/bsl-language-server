import type { DefaultTheme } from 'vitepress'
import { diagnosticPages } from './pages'

type Locale = '' | 'en'

interface Labels {
  overview: string
  capabilities: string
  diagnostics: string
  diagnosticsCatalog: string
  diagnosticsAll: string
  usage: string
  reporters: string
  features: string
  faq: string
  requirements: string
  development: string
  contributing: string
  javadoc: string
  benchmarks: string
  start: string
  diagnosticsDev: string
  internals: string
}

const RU: Labels = {
  overview: 'Обзор',
  capabilities: 'Возможности',
  diagnostics: 'Диагностики',
  diagnosticsCatalog: 'Каталог диагностик',
  diagnosticsAll: 'Все диагностики',
  usage: 'Использование',
  reporters: 'Репортеры',
  features: 'Дополнительные возможности',
  faq: 'Частые вопросы',
  requirements: 'Системные требования',
  development: 'Разработка',
  contributing: 'Руководство контрибьютора',
  javadoc: 'JavaDoc',
  benchmarks: 'Замеры производительности',
  start: 'Старт',
  diagnosticsDev: 'Разработка диагностик',
  internals: 'Внутреннее устройство',
}

const EN: Labels = {
  overview: 'Overview',
  capabilities: 'Capabilities',
  diagnostics: 'Diagnostics',
  diagnosticsCatalog: 'Diagnostics catalog',
  diagnosticsAll: 'All diagnostics',
  usage: 'Usage',
  reporters: 'Reporters',
  features: 'Features',
  faq: 'FAQ',
  requirements: 'System requirements',
  development: 'Development',
  contributing: 'Contributing guidelines',
  javadoc: 'JavaDoc',
  benchmarks: 'Benchmarks',
  start: 'Getting started',
  diagnosticsDev: 'Developing diagnostics',
  internals: 'Internals',
}

/** Возможности перечислены в том же порядке, что и в старом `mkdocs.yml`. */
const CAPABILITIES: Array<[string, string, string]> = [
  ['completion', 'Автодополнение кода', 'Code completion'],
  ['definition', 'Переход к определению', 'Go to definition'],
  ['implementation', 'Переход к реализациям', 'Go to implementation'],
  ['references', 'Поиск использований', 'Find references'],
  ['hover', 'Всплывающая документация', 'Hover'],
  ['signatureHelp', 'Подсказка по параметрам', 'Signature help'],
  ['diagnostics', 'Диагностики', 'Diagnostics'],
  ['codeAction', 'Быстрые исправления', 'Quick fixes'],
  ['formatting', 'Форматирование', 'Formatting'],
  ['rename', 'Переименование', 'Rename'],
  ['linkedEditing', 'Связанное редактирование', 'Linked editing'],
  ['documentSymbol', 'Структура документа', 'Document symbols'],
  ['workspaceSymbol', 'Поиск по проекту', 'Workspace symbols'],
  ['documentHighlight', 'Подсветка вхождений', 'Document highlight'],
  ['callHierarchy', 'Иерархия вызовов', 'Call hierarchy'],
  ['typeHierarchy', 'Иерархия типов', 'Type hierarchy'],
  ['folding', 'Сворачивание блоков', 'Folding'],
  ['selectionRange', 'Умное выделение', 'Selection range'],
  ['semanticTokens', 'Семантическая подсветка', 'Semantic tokens'],
  ['inlayHint', 'Встроенные подсказки', 'Inlay hints'],
  ['codeLens', 'Код-линзы', 'Code lenses'],
  ['color', 'Цвета: превью и палитра', 'Color provider'],
  ['documentLink', 'Гиперссылки в коде', 'Document links'],
]

const FEATURES: Array<[string, string, string]> = [
  ['ConfigurationFile', 'Конфигурационный файл', 'Configuration file'],
  ['DiagnosticIgnorance', 'Экранирование участков кода', 'Ignoring code fragments'],
  ['McpMode', 'Режим MCP', 'MCP mode'],
  ['Monitoring', 'Мониторинг', 'Monitoring'],
]

const REPORTERS: Array<[string, string, string]> = [
  ['json', 'JSON', 'JSON'],
  ['generic', 'Generic Issue', 'Generic Issue'],
  ['sarif', 'SARIF', 'SARIF'],
  ['junit', 'JUnit', 'JUnit'],
  ['tslint', 'TSLint', 'TSLint'],
  ['code-quality', 'Code Quality', 'Code Quality'],
  ['console', 'Console', 'Console'],
]

const CONTRIBUTING_START: Array<[string, string, string]> = [
  ['EnvironmentSetting', 'Настройка окружения', 'Environment setup'],
  ['FastStart', 'Быстрый старт', 'Fast start'],
  ['StyleGuide', 'Стиль кода', 'Style guide'],
]

const CONTRIBUTING_DIAGNOSTICS: Array<[string, string, string]> = [
  ['DiagnosticDevWorkFlow', 'Процесс разработки', 'Development workflow'],
  ['DiagnosticExample', 'Пример диагностики', 'Diagnostic example'],
  ['DiagnosticStructure', 'Структура диагностики', 'Diagnostic structure'],
  ['DiagnosticTypeAndSeverity', 'Тип и важность', 'Type and severity'],
  ['DiagnosticTag', 'Теги', 'Tags'],
  ['DiagnostcAddSettings', 'Параметры диагностики', 'Diagnostic settings'],
  ['DiagnosticQuickFix', 'Быстрые исправления', 'Quick fixes'],
]

const CONTRIBUTING_INTERNALS: Array<[string, string, string]> = [
  ['EventsApi', 'События и подписки', 'Events API'],
  ['Measures', 'Замеры производительности', 'Measures'],
]

function items(
  base: string,
  locale: Locale,
  entries: Array<[string, string, string]>,
): DefaultTheme.SidebarItem[] {
  return entries.map(([file, ru, en]) => ({
    text: locale === 'en' ? en : ru,
    link: `${base}${file}/`,
  }))
}

function root(locale: Locale): string {
  return locale === 'en' ? '/en/' : '/'
}

export function nav(locale: Locale): DefaultTheme.NavItem[] {
  const l = locale === 'en' ? EN : RU
  const r = root(locale)

  return [
    { text: l.overview, link: r, activeMatch: `^${r}$` },
    { text: l.capabilities, link: `${r}capabilities/`, activeMatch: `${r}capabilities/` },
    { text: l.diagnostics, link: `${r}diagnostics/`, activeMatch: `${r}diagnostics/` },
    {
      text: l.usage,
      activeMatch: `${r}(features|reporters|faq|systemRequirements)`,
      items: [
        { text: l.features, link: `${r}features/` },
        { text: l.reporters, link: `${r}reporters/` },
        { text: l.faq, link: `${r}faq/` },
        { text: l.requirements, link: `${r}systemRequirements/` },
      ],
    },
    {
      text: l.development,
      activeMatch: `${r}contributing/`,
      items: [
        { text: l.contributing, link: `${r}contributing/` },
        { text: l.javadoc, link: '/javadoc/index.html', target: '_blank' },
        { text: l.benchmarks, link: '/bench/index.html', target: '_blank' },
      ],
    },
  ]
}

export function sidebar(locale: Locale, docsRoot: string): DefaultTheme.SidebarMulti {
  const l = locale === 'en' ? EN : RU
  const r = root(locale)

  const capabilities: DefaultTheme.SidebarItem[] = [
    {
      text: l.capabilities,
      items: [{ text: l.overview, link: `${r}capabilities/` }, ...items(`${r}capabilities/`, locale, CAPABILITIES)],
    },
  ]

  const diagnostics: DefaultTheme.SidebarItem[] = [
    {
      text: l.diagnostics,
      items: [
        { text: l.diagnosticsCatalog, link: `${r}diagnostics/` },
        {
          text: l.diagnosticsAll,
          collapsed: true,
          items: diagnosticPages(docsRoot, locale).map((page) => ({
            text: page.key,
            link: page.link,
          })),
        },
      ],
    },
  ]

  const usage: DefaultTheme.SidebarItem[] = [
    {
      text: l.features,
      items: [{ text: l.overview, link: `${r}features/` }, ...items(`${r}features/`, locale, FEATURES)],
    },
    {
      text: l.reporters,
      items: [{ text: l.overview, link: `${r}reporters/` }, ...items(`${r}reporters/`, locale, REPORTERS)],
    },
    {
      text: l.usage,
      items: [
        { text: l.faq, link: `${r}faq/` },
        { text: l.requirements, link: `${r}systemRequirements/` },
      ],
    },
  ]

  const contributing: DefaultTheme.SidebarItem[] = [
    {
      text: l.contributing,
      items: [{ text: l.overview, link: `${r}contributing/` }],
    },
    { text: l.start, items: items(`${r}contributing/`, locale, CONTRIBUTING_START) },
    { text: l.diagnosticsDev, items: items(`${r}contributing/`, locale, CONTRIBUTING_DIAGNOSTICS) },
    { text: l.internals, items: items(`${r}contributing/`, locale, CONTRIBUTING_INTERNALS) },
  ]

  return {
    [`${r}capabilities/`]: capabilities,
    [`${r}diagnostics/`]: diagnostics,
    [`${r}features/`]: usage,
    [`${r}reporters/`]: usage,
    [`${r}faq/`]: usage,
    [`${r}systemRequirements/`]: usage,
    [`${r}contributing/`]: contributing,
  }
}
