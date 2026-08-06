<script setup lang="ts">
import { computed, ref } from 'vue'
import { useData, withBase } from 'vitepress'

const { lang } = useData()
const isRu = computed(() => lang.value.startsWith('ru'))
const root = computed(() => (isRu.value ? '/' : '/en/'))

interface Feature {
  icon: string
  title: string
  text: string
  link?: string
}

const features = computed<Feature[]>(() =>
  isRu.value
    ? [
        {
          icon: 'shield',
          title: 'Движок диагностик',
          text: 'Проверки на соответствие стандартам разработки 1С, поиск ошибок и уязвимостей. Каждое правило настраивается и документировано.',
          link: 'diagnostics/',
        },
        {
          icon: 'plug',
          title: 'Любой редактор с LSP',
          text: 'VS Code, 1C:EDT, IntelliJ IDEA, Vim, Emacs. Автодополнение, переходы, hover, переименование, code actions.',
          link: 'capabilities/',
        },
        {
          icon: 'terminal',
          title: 'Пакетный анализ для CI',
          text: 'Режим analyze прогоняет весь проект и выгружает отчёты в SARIF, Generic Issue, JUnit и другие форматы.',
          link: 'reporters/',
        },
        {
          icon: 'wand',
          title: 'Форматирование кода',
          text: 'Форматирование документа, выделенного фрагмента и по мере набора — из редактора или из командной строки.',
          link: 'capabilities/formatting/',
        },
        {
          icon: 'sparkles',
          title: 'Режим MCP',
          text: 'Model Context Protocol: языковой сервер отдаёт AI-ассистентам структуру кода и результаты анализа.',
          link: 'features/McpMode/',
        },
        {
          icon: 'sliders',
          title: 'Гибкая настройка',
          text: 'Конфигурационный файл, экранирование участков кода комментариями и параметры у каждой диагностики.',
          link: 'features/ConfigurationFile/',
        },
      ]
    : [
        {
          icon: 'shield',
          title: 'Diagnostics engine',
          text: '1C development standards checks, bug and vulnerability detection. Every rule is documented and configurable.',
          link: 'diagnostics/',
        },
        {
          icon: 'plug',
          title: 'Any LSP-capable editor',
          text: 'VS Code, 1C:EDT, IntelliJ IDEA, Vim, Emacs. Completion, navigation, hover, rename and code actions.',
          link: 'capabilities/',
        },
        {
          icon: 'terminal',
          title: 'Batch analysis for CI',
          text: 'The analyze mode scans the whole project and exports SARIF, Generic Issue, JUnit and other reports.',
          link: 'reporters/',
        },
        {
          icon: 'wand',
          title: 'Code formatting',
          text: 'Format the document, a selected range or as you type — from the editor or from the command line.',
          link: 'capabilities/formatting/',
        },
        {
          icon: 'sparkles',
          title: 'MCP mode',
          text: 'Model Context Protocol: the language server exposes code structure and analysis results to AI assistants.',
          link: 'features/McpMode/',
        },
        {
          icon: 'sliders',
          title: 'Fine-grained configuration',
          text: 'A configuration file, comment-based suppressions and per-diagnostic parameters.',
          link: 'features/ConfigurationFile/',
        },
      ],
)

interface Mode {
  id: string
  label: string
  hint: string
  command: string
}

const modes = computed<Mode[]>(() =>
  isRu.value
    ? [
        {
          id: 'lsp',
          label: 'lsp',
          hint: 'Языковой сервер поверх stdin/stdout — режим по умолчанию, его запускает сам редактор.',
          command: 'java -jar bsl-language-server.jar lsp \\\n  --configuration .bsl-language-server.json',
        },
        {
          id: 'analyze',
          label: 'analyze',
          hint: 'Пакетный анализ каталога с выгрузкой отчётов — то, что нужно на сборочной линии.',
          command:
            'java -jar bsl-language-server.jar analyze \\\n  --srcDir ./src \\\n  --reporter sarif \\\n  --reporter generic',
        },
        {
          id: 'format',
          label: 'format',
          hint: 'Форматирование всех модулей в каталоге по правилам языкового сервера.',
          command: 'java -jar bsl-language-server.jar format \\\n  --src ./src',
        },
        {
          id: 'websocket',
          label: 'websocket',
          hint: 'Тот же LSP, но по websocket — для веб-редакторов и удалённых клиентов.',
          command: 'java -jar bsl-language-server.jar websocket \\\n  --server.port 8025',
        },
        {
          id: 'mcp',
          label: 'mcp',
          hint: 'Сервер Model Context Protocol: stdio, SSE или Streamable HTTP.',
          command: 'java -jar bsl-language-server.jar mcp \\\n  --transport stdio',
        },
      ]
    : [
        {
          id: 'lsp',
          label: 'lsp',
          hint: 'Language server over stdin/stdout — the default mode, started by the editor itself.',
          command: 'java -jar bsl-language-server.jar lsp \\\n  --configuration .bsl-language-server.json',
        },
        {
          id: 'analyze',
          label: 'analyze',
          hint: 'Batch analysis of a directory with report export — what you want on the build agent.',
          command:
            'java -jar bsl-language-server.jar analyze \\\n  --srcDir ./src \\\n  --reporter sarif \\\n  --reporter generic',
        },
        {
          id: 'format',
          label: 'format',
          hint: 'Format every module in a directory using the language server rules.',
          command: 'java -jar bsl-language-server.jar format \\\n  --src ./src',
        },
        {
          id: 'websocket',
          label: 'websocket',
          hint: 'The same LSP, but over websocket — for web editors and remote clients.',
          command: 'java -jar bsl-language-server.jar websocket \\\n  --server.port 8025',
        },
        {
          id: 'mcp',
          label: 'mcp',
          hint: 'Model Context Protocol server: stdio, SSE or Streamable HTTP.',
          command: 'java -jar bsl-language-server.jar mcp \\\n  --transport stdio',
        },
      ],
)

const active = ref(0)

const titles = computed(() =>
  isRu.value
    ? { features: 'Что умеет сервер', modes: 'Пять режимов запуска', modesLead: 'Одна поставка — консольное приложение на Java. Режим выбирается подкомандой.' }
    : {
        features: 'What the server does',
        modes: 'Five run modes',
        modesLead: 'A single artifact — a Java console application. The mode is picked by a subcommand.',
      },
)

const ICONS: Record<string, string> = {
  shield: 'M12 3l7 3v6c0 4.2-2.9 7.9-7 9-4.1-1.1-7-4.8-7-9V6l7-3z M9.2 11.8l2 2 3.6-3.8',
  plug: 'M9 3v6 M15 3v6 M6 9h12v3a6 6 0 0 1-6 6 6 6 0 0 1-6-6V9z M12 18v3',
  terminal: 'M4 5h16v14H4z M7.5 9.5l3 2.5-3 2.5 M13 15h4',
  wand: 'M5 19L19 5 M15 5l4 4 M8 3v4 M6 5h4 M16 15v4 M14 17h4',
  sparkles: 'M12 3l1.8 4.7L18.5 9.5 13.8 11.3 12 16l-1.8-4.7L5.5 9.5l4.7-1.8z M18 16l.9 2.1 2.1.9-2.1.9L18 22l-.9-2.1-2.1-.9 2.1-.9z',
  sliders: 'M4 7h10 M18 7h2 M4 12h4 M12 12h8 M4 17h12 M20 17h0 M14 5v4 M8 10v4 M16 15v4',
}
</script>

<template>
  <section class="bsl-highlights">
    <div class="bsl-section">
      <h2 class="bsl-section__title">{{ titles.features }}</h2>

      <div class="bsl-cards">
        <component
          :is="feature.link ? 'a' : 'div'"
          v-for="feature in features"
          :key="feature.title"
          class="bsl-card"
          :href="feature.link ? withBase(root + feature.link) : undefined"
        >
          <span class="bsl-card__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path v-for="(d, i) in ICONS[feature.icon].split(' M')" :key="i" :d="i === 0 ? d : 'M' + d" />
            </svg>
          </span>
          <h3 class="bsl-card__title">{{ feature.title }}</h3>
          <p class="bsl-card__text">{{ feature.text }}</p>
        </component>
      </div>
    </div>

    <div class="bsl-section">
      <h2 class="bsl-section__title">{{ titles.modes }}</h2>
      <p class="bsl-section__lead">{{ titles.modesLead }}</p>

      <div class="bsl-modes">
        <div class="bsl-modes__tabs" role="tablist">
          <button
            v-for="(mode, index) in modes"
            :key="mode.id"
            class="bsl-modes__tab"
            :class="{ 'is-active': active === index }"
            role="tab"
            :aria-selected="active === index"
            type="button"
            @click="active = index"
          >
            {{ mode.label }}
          </button>
        </div>

        <div class="bsl-modes__panel">
          <p class="bsl-modes__hint">{{ modes[active].hint }}</p>
          <pre class="bsl-modes__command"><code>{{ modes[active].command.replace(/\\\n/g, '\n') }}</code></pre>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.bsl-highlights {
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 24px;
}

@media (min-width: 1024px) {
  .bsl-highlights {
    padding: 0 32px;
  }
}

.bsl-section {
  padding: 64px 0 0;
}

.bsl-section__title {
  position: relative;
  margin: 0 0 8px;
  padding-left: 18px;
  font-family: var(--bsl-font-display);
  font-size: clamp(1.35rem, 1.1rem + 0.8vw, 1.85rem);
  font-weight: 800;
  letter-spacing: -0.035em;
  color: var(--vp-c-text-1);
}

.bsl-section__title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.22em;
  bottom: 0.22em;
  width: 4px;
  border-radius: 4px;
  background: var(--bsl-gradient);
}

.bsl-section__lead {
  margin: 0 0 26px 18px;
  max-width: 62ch;
  color: var(--vp-c-text-2);
  line-height: 1.65;
}

.bsl-section__title + .bsl-cards {
  margin-top: 28px;
}

/* ------------------------------------------------------------- карточки */

.bsl-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.bsl-card {
  position: relative;
  display: block;
  padding: 24px;
  border: 1px solid var(--vp-c-divider);
  border-radius: var(--bsl-radius-lg);
  background: var(--bsl-surface);
  color: inherit;
  text-decoration: none;
  overflow: hidden;
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.bsl-card::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0;
  background: var(--bsl-gradient-soft);
  transition: opacity 0.25s;
}

a.bsl-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--bsl-amber) 55%, var(--vp-c-divider));
  box-shadow: var(--bsl-shadow);
}

a.bsl-card:hover::before {
  opacity: 1;
}

.bsl-card > * {
  position: relative;
}

.bsl-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  margin-bottom: 16px;
  border-radius: 12px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-brand-1);
}

.bsl-card__icon svg {
  width: 21px;
  height: 21px;
}

.bsl-card__title {
  margin: 0 0 8px;
  font-family: var(--bsl-font-display);
  font-size: 1.02rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--vp-c-text-1);
}

.bsl-card__text {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.62;
  color: var(--vp-c-text-2);
}

/* --------------------------------------------------------------- режимы */

.bsl-modes {
  border: 1px solid var(--vp-c-divider);
  border-radius: var(--bsl-radius-lg);
  background: var(--bsl-surface);
  overflow: hidden;
  box-shadow: var(--bsl-shadow);
}

.bsl-modes__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px;
  border-bottom: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}

.bsl-modes__tab {
  padding: 7px 16px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  font-family: var(--bsl-font-mono);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--vp-c-text-2);
  cursor: pointer;
  transition: background-color 0.18s, color 0.18s, border-color 0.18s;
}

.bsl-modes__tab:hover {
  color: var(--vp-c-text-1);
  border-color: var(--vp-c-divider);
}

.bsl-modes__tab.is-active {
  background: var(--bsl-gradient);
  color: #1a1206;
  font-weight: 700;
}

.bsl-modes__panel {
  padding: 22px;
}

.bsl-modes__hint {
  margin: 0 0 16px;
  max-width: 70ch;
  font-size: 0.92rem;
  line-height: 1.6;
  color: var(--vp-c-text-2);
}

.bsl-modes__command {
  margin: 0;
  padding: 18px 20px;
  border-radius: var(--bsl-radius);
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
  overflow-x: auto;
  font-family: var(--bsl-font-mono);
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--vp-c-text-1);
  white-space: pre;
}

.dark .bsl-modes__command {
  background: #0d1117;
}
</style>
