<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()
const isRu = computed(() => lang.value.startsWith('ru'))

const copy = computed(() =>
  isRu.value
    ? {
        file: 'РасчётОстатков.bsl',
        message: 'Выполнение запроса в цикле',
        severity: 'Критичный',
        hint: 'Необходимо модифицировать запрос для поддержки множества значений и удалить цикл',
      }
    : {
        file: 'StockBalance.bsl',
        message: 'Execution query on cycle',
        severity: 'Critical',
        hint: 'Modify query to support multiple values and remove cycle',
      },
)
</script>

<template>
  <figure class="bsl-editor" role="img" :aria-label="copy.message">
    <div class="bsl-editor__chrome">
      <span class="bsl-editor__dot bsl-editor__dot--red" />
      <span class="bsl-editor__dot bsl-editor__dot--amber" />
      <span class="bsl-editor__dot bsl-editor__dot--green" />
      <span class="bsl-editor__file">{{ copy.file }}</span>
      <span class="bsl-editor__badge">BSL</span>
    </div>

    <pre class="bsl-editor__code"><code><span class="ln">1</span><span class="kw">Процедура</span> <span class="fn">ЗаполнитьОстатки</span>(<span class="pm">Заказы</span>) <span class="kw">Экспорт</span>
<span class="ln">2</span>
<span class="ln">3</span>	<span class="pm">Запрос</span> = <span class="kw">Новый</span> <span class="cm">Запрос</span>(<span class="pm">ТекстЗапроса</span>);
<span class="ln">4</span>
<span class="ln">5</span>	<span class="kw">Для Каждого</span> <span class="pm">Заказ</span> <span class="kw">Из</span> <span class="pm">Заказы</span> <span class="kw">Цикл</span>
<span class="ln">6</span>		<span class="pm">Запрос</span>.<span class="fn">УстановитьПараметр</span>(<span class="str">"Товар"</span>, <span class="pm">Заказ</span>.Товар);
<span class="ln">7</span>		<span class="pm">Выборка</span> = <span class="pm">Запрос</span>.<span class="err">Выполнить</span>().<span class="fn">Выбрать</span>();
<span class="ln">8</span>	<span class="kw">КонецЦикла</span>;
<span class="ln">9</span>
<span class="ln">10</span><span class="kw">КонецПроцедуры</span></code></pre>

    <figcaption class="bsl-editor__diagnostic">
      <span class="bsl-editor__severity">{{ copy.severity }}</span>
      <div class="bsl-editor__body">
        <p class="bsl-editor__message">{{ copy.message }}</p>
        <p class="bsl-editor__meta"><code>BSLLS:CreateQueryInCycle</code> · {{ copy.hint }}</p>
      </div>
    </figcaption>
  </figure>
</template>

<style scoped>
.bsl-editor {
  position: relative;
  margin: 0;
  border: 1px solid var(--vp-c-divider);
  border-radius: var(--bsl-radius-lg);
  background: var(--bsl-surface);
  box-shadow: var(--bsl-shadow-lg);
  overflow: hidden;
}

.bsl-editor__chrome {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}

.bsl-editor__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  opacity: 0.85;
}

.bsl-editor__dot--red {
  background: #ff5f57;
}
.bsl-editor__dot--amber {
  background: #febc2e;
}
.bsl-editor__dot--green {
  background: #28c840;
}

.bsl-editor__file {
  margin-left: 10px;
  font-family: var(--bsl-font-mono);
  font-size: 12px;
  color: var(--vp-c-text-2);
}

.bsl-editor__badge {
  margin-left: auto;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--vp-c-brand-soft);
  font-family: var(--bsl-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--vp-c-brand-1);
}

.bsl-editor__code {
  margin: 0;
  padding: 20px 20px 24px 0;
  overflow-x: auto;
  font-family: var(--bsl-font-mono);
  font-size: 12.5px;
  line-height: 1.85;
  tab-size: 2;
  color: var(--vp-c-text-1);
}

.bsl-editor__code code {
  display: block;
  min-width: max-content;
}

.ln {
  display: inline-block;
  width: 44px;
  padding-right: 16px;
  text-align: right;
  color: var(--vp-c-text-3);
  opacity: 0.55;
  user-select: none;
}

.kw {
  color: #b6427a;
  font-weight: 500;
}
.fn {
  color: #2a6fd6;
}
.pm {
  color: #8a6d1f;
}
.str {
  color: #1f7a4d;
}
.cm {
  color: #7d5bb0;
}

.dark .kw {
  color: #ff7ab2;
}
.dark .fn {
  color: #79b8ff;
}
.dark .pm {
  color: #ffc061;
}
.dark .str {
  color: #7ee787;
}
.dark .cm {
  color: #c4a2ff;
}

/* Подчёркивание проблемного вызова — как в редакторе. */
.err {
  position: relative;
  color: inherit;
  text-decoration-line: underline;
  text-decoration-style: wavy;
  text-decoration-color: var(--bsl-coral);
  text-decoration-thickness: 1px;
  text-underline-offset: 4px;
}

.bsl-editor__diagnostic {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin: 0 16px 16px;
  padding: 14px 16px;
  border: 1px solid var(--vp-c-divider);
  border-left: 3px solid var(--bsl-amber);
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
}

.bsl-editor__severity {
  flex-shrink: 0;
  margin-top: 1px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--vp-c-brand-soft);
  font-family: var(--bsl-font-display);
  font-size: 11px;
  font-weight: 700;
  color: var(--vp-c-brand-1);
  white-space: nowrap;
}

.bsl-editor__body {
  min-width: 0;
}

.bsl-editor__message {
  margin: 0;
  font-size: 0.86rem;
  font-weight: 600;
  line-height: 1.45;
  color: var(--vp-c-text-1);
}

.bsl-editor__meta {
  margin: 6px 0 0;
  font-size: 0.76rem;
  line-height: 1.5;
  color: var(--vp-c-text-3);
}

.bsl-editor__meta code {
  font-family: var(--bsl-font-mono);
  font-size: 0.95em;
  color: var(--vp-c-text-2);
}
</style>
