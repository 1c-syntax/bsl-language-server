<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()
const isRu = computed(() => lang.value.startsWith('ru'))

/** Члены `ТаблицаЗначений` — тот набор, что языковой сервер отдаёт после точки. */
const MEMBERS = [
  { name: 'Итог', signature: '(<Колонка>)', returns: 'Число', active: true },
  { name: 'НайтиСтроки', signature: '(<Отбор>)', returns: 'Массив' },
  { name: 'Сортировать', signature: '(<Колонки>)', returns: '' },
  { name: 'Свернуть', signature: '(<Колонки>, <КолонкиСуммирования>)', returns: '' },
  { name: 'Скопировать', signature: '()', returns: 'ТаблицаЗначений' },
]

const copy = computed(() =>
  isRu.value
    ? {
        file: 'РасчётЗаказа.bsl',
        doc: 'Подсчитывает сумму значений числовой колонки по всем строкам таблицы.',
        legend: 'Тип из документирующего комментария → вывод типов переменных → члены после точки',
      }
    : {
        file: 'OrderTotals.bsl',
        doc: 'Calculates the sum of a numeric column across all rows of the table.',
        legend: 'Type from the doc comment → inferred variable types → members after the dot',
      },
)
</script>

<template>
  <figure class="bsl-editor">
    <div class="bsl-editor__chrome">
      <span class="bsl-editor__dot bsl-editor__dot--red" />
      <span class="bsl-editor__dot bsl-editor__dot--amber" />
      <span class="bsl-editor__dot bsl-editor__dot--green" />
      <span class="bsl-editor__file">{{ copy.file }}</span>
      <span class="bsl-editor__badge">BSL</span>
    </div>

    <pre class="bsl-editor__code"><code><span class="ln">1</span><span class="cmt">// Параметры:</span>
<span class="ln">2</span><span class="cmt">//   Таблица - ТаблицаЗначений - строки заказа</span>
<span class="ln">3</span><span class="kw">Процедура</span> <span class="fn">РассчитатьИтоги</span>(<span class="pm">Таблица</span>) <span class="kw">Экспорт</span>
<span class="ln">4</span>
<span class="ln">5</span>	<span class="pm">Колонки</span><span class="hint">: КоллекцияКолонокТаблицыЗначений</span> = <span class="pm">Таблица</span>.Колонки;
<span class="ln">6</span>	<span class="pm">Всего</span><span class="hint">: Число</span> = <span class="pm">Таблица</span>.<span class="fn">Количество</span>();
<span class="ln">7</span>
<span class="ln">8</span>	<span class="pm">Таблица</span>.<span class="caret" /></code></pre>

    <div class="bsl-editor__completion">
      <ul class="bsl-editor__list">
        <li
          v-for="member in MEMBERS"
          :key="member.name"
          class="bsl-editor__item"
          :class="{ 'is-active': member.active }"
        >
          <span class="bsl-editor__kind">ƒ</span>
          <span class="bsl-editor__name">{{ member.name }}<span class="bsl-editor__signature">{{ member.signature }}</span></span>
          <span v-if="member.returns" class="bsl-editor__returns">{{ member.returns }}</span>
        </li>
      </ul>

      <div class="bsl-editor__docs">
        <code>Итог(&lt;Колонка&gt;) : Число</code>
        <p>{{ copy.doc }}</p>
      </div>
    </div>

    <figcaption class="bsl-editor__legend">{{ copy.legend }}</figcaption>
  </figure>
</template>

<style scoped>
.bsl-editor {
  position: relative;
  margin: 0;
  padding-bottom: 18px;
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
  padding: 18px 20px 0 0;
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
.cmt {
  color: #6f7a86;
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
.dark .cmt {
  color: #7d8896;
}

/* Подсказка-вставка с выведенным типом — как её рисует редактор. */
.hint {
  margin: 0 1px;
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--vp-c-brand-soft);
  font-size: 0.86em;
  color: var(--vp-c-brand-1);
  opacity: 0.95;
}

.caret {
  display: inline-block;
  width: 1px;
  height: 1.05em;
  vertical-align: -0.2em;
  background: var(--vp-c-brand-1);
  animation: bsl-blink 1.15s steps(2, start) infinite;
}

@keyframes bsl-blink {
  to {
    visibility: hidden;
  }
}

@media (prefers-reduced-motion: reduce) {
  .caret {
    animation: none;
  }
}

/* ---------------------------------------------------- список автодополнения */

.bsl-editor__completion {
  margin: 2px 16px 0 62px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background: var(--vp-c-bg-elv);
  box-shadow: var(--bsl-shadow);
  overflow: hidden;
}

.bsl-editor__list {
  margin: 0;
  padding: 6px;
  list-style: none;
}

.bsl-editor__item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 5px 10px;
  border-radius: 7px;
  font-family: var(--bsl-font-mono);
  font-size: 12px;
  color: var(--vp-c-text-2);
  white-space: nowrap;
}

.bsl-editor__item.is-active {
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-text-1);
}

.bsl-editor__kind {
  flex-shrink: 0;
  width: 16px;
  text-align: center;
  font-style: italic;
  color: var(--vp-c-brand-1);
}

.bsl-editor__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bsl-editor__signature {
  color: var(--vp-c-text-3);
}

.bsl-editor__returns {
  flex-shrink: 0;
  color: var(--vp-c-text-3);
  font-size: 11px;
}

.bsl-editor__docs {
  padding: 12px 16px;
  border-top: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}

.bsl-editor__docs code {
  font-family: var(--bsl-font-mono);
  font-size: 11.5px;
  color: var(--vp-c-brand-1);
}

.bsl-editor__docs p {
  margin: 6px 0 0;
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--vp-c-text-2);
}

.bsl-editor__legend {
  padding: 14px 20px 0;
  font-size: 0.74rem;
  line-height: 1.5;
  color: var(--vp-c-text-3);
}

@media (max-width: 640px) {
  .bsl-editor__completion {
    margin-left: 16px;
  }

  .bsl-editor__returns {
    display: none;
  }
}
</style>
