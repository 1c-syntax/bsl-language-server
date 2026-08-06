<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useData, useRoute } from 'vitepress'

/**
 * Живой фильтр поверх сгенерированной таблицы диагностик.
 *
 * Саму таблицу собирает gradle-задача `generateDiagnosticsIndex`, поэтому компонент не
 * дублирует данные, а работает как прогрессивное улучшение уже отрисованной разметки:
 * читает строки из DOM и прячет неподходящие. Без JS страница остаётся полностью рабочей.
 */

const COL = { key: 0, name: 1, enabled: 2, severity: 3, type: 4, tags: 5 }

const { lang } = useData()
const route = useRoute()
const isRu = computed(() => lang.value.startsWith('ru'))

const copy = computed(() =>
  isRu.value
    ? {
        placeholder: 'Поиск по ключу, названию или тегу…',
        type: 'Тип',
        severity: 'Важность',
        enabledOnly: 'Только включённые по умолчанию',
        reset: 'Сбросить',
        shown: (n: number, total: number) => `Показано ${n} из ${total}`,
        empty: 'Ничего не найдено',
      }
    : {
        placeholder: 'Search by key, name or tag…',
        type: 'Type',
        severity: 'Severity',
        enabledOnly: 'Enabled by default only',
        reset: 'Reset',
        shown: (n: number, total: number) => `Showing ${n} of ${total}`,
        empty: 'Nothing found',
      },
)

const query = ref('')
const selectedTypes = ref<string[]>([])
const selectedSeverities = ref<string[]>([])
const enabledOnly = ref(false)

const types = ref<string[]>([])
const severities = ref<string[]>([])
const total = ref(0)
const visible = ref(0)

const self = ref<HTMLElement | null>(null)
let rows: HTMLTableRowElement[] = []
let table: HTMLTableElement | null = null

function cell(row: HTMLTableRowElement, index: number): string {
  return row.cells[index]?.textContent?.trim() ?? ''
}

function collect() {
  table = document.querySelector('.vp-doc table')
  rows = table ? Array.from(table.querySelectorAll('tbody tr')) : []
  total.value = rows.length

  const uniq = (index: number) =>
    Array.from(new Set(rows.map((row) => cell(row, index)).filter(Boolean))).sort((a, b) =>
      a.localeCompare(b),
    )

  types.value = uniq(COL.type)
  severities.value = uniq(COL.severity)

  // Компонент отрисован в начале статьи, но полезен он вплотную к таблице — переносим.
  const anchor = table?.closest('.table-wrapper') ?? table
  if (self.value && anchor?.parentElement && self.value.nextElementSibling !== anchor) {
    anchor.parentElement.insertBefore(self.value, anchor)
  }

  apply()
}

function matches(row: HTMLTableRowElement): boolean {
  const text = query.value.trim().toLowerCase()
  if (text) {
    const haystack = [cell(row, COL.key), cell(row, COL.name), cell(row, COL.tags)]
      .join(' ')
      .toLowerCase()
    if (!haystack.includes(text)) {
      return false
    }
  }

  if (selectedTypes.value.length && !selectedTypes.value.includes(cell(row, COL.type))) {
    return false
  }

  if (selectedSeverities.value.length && !selectedSeverities.value.includes(cell(row, COL.severity))) {
    return false
  }

  if (enabledOnly.value) {
    const value = cell(row, COL.enabled).toLowerCase()
    if (value !== 'да' && value !== 'yes') {
      return false
    }
  }

  return true
}

function apply() {
  let shown = 0
  for (const row of rows) {
    const ok = matches(row)
    row.style.display = ok ? '' : 'none'
    if (ok) shown++
  }
  visible.value = shown
}

function toggle(list: typeof selectedTypes, value: string) {
  const index = list.value.indexOf(value)
  if (index === -1) {
    list.value = [...list.value, value]
  } else {
    list.value = list.value.filter((item) => item !== value)
  }
}

function reset() {
  query.value = ''
  selectedTypes.value = []
  selectedSeverities.value = []
  enabledOnly.value = false
}

const dirty = computed(
  () =>
    query.value !== '' ||
    selectedTypes.value.length > 0 ||
    selectedSeverities.value.length > 0 ||
    enabledOnly.value,
)

watch([query, selectedTypes, selectedSeverities, enabledOnly], apply)
watch(() => route.path, () => nextTick(collect))

onMounted(() => nextTick(collect))
onUnmounted(() => {
  for (const row of rows) {
    row.style.display = ''
  }
})
</script>

<template>
  <div v-show="total > 0" ref="self" class="bsl-filter">
    <div class="bsl-filter__search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
        <circle cx="11" cy="11" r="7" />
        <path d="M20 20l-3.5-3.5" stroke-linecap="round" />
      </svg>
      <input v-model="query" type="search" :placeholder="copy.placeholder" :aria-label="copy.placeholder" />
      <span class="bsl-filter__count">{{ copy.shown(visible, total) }}</span>
    </div>

    <div class="bsl-filter__row">
      <span class="bsl-filter__label">{{ copy.type }}</span>
      <button
        v-for="type in types"
        :key="type"
        type="button"
        class="bsl-chip"
        :class="{ 'is-active': selectedTypes.includes(type) }"
        @click="toggle(selectedTypes, type)"
      >
        {{ type }}
      </button>
    </div>

    <div class="bsl-filter__row">
      <span class="bsl-filter__label">{{ copy.severity }}</span>
      <button
        v-for="severity in severities"
        :key="severity"
        type="button"
        class="bsl-chip"
        :class="{ 'is-active': selectedSeverities.includes(severity) }"
        @click="toggle(selectedSeverities, severity)"
      >
        {{ severity }}
      </button>
    </div>

    <div class="bsl-filter__row">
      <button
        type="button"
        class="bsl-chip"
        :class="{ 'is-active': enabledOnly }"
        @click="enabledOnly = !enabledOnly"
      >
        {{ copy.enabledOnly }}
      </button>
      <button v-if="dirty" type="button" class="bsl-chip bsl-chip--reset" @click="reset">
        {{ copy.reset }}
      </button>
    </div>

    <p v-if="visible === 0" class="bsl-filter__empty">{{ copy.empty }}</p>
  </div>
</template>

<style scoped>
.bsl-filter {
  margin: 28px 0 8px;
  padding: 18px;
  border: 1px solid var(--vp-c-divider);
  border-radius: var(--bsl-radius-lg);
  background: var(--vp-c-bg-soft);
}

.bsl-filter__search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 999px;
  background: var(--vp-c-bg);
  transition: border-color 0.2s;
}

.bsl-filter__search:focus-within {
  border-color: var(--vp-c-brand-3);
}

.bsl-filter__search svg {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  color: var(--vp-c-text-3);
}

.bsl-filter__search input {
  flex: 1;
  min-width: 0;
  height: 40px;
  border: none;
  background: transparent;
  outline: none;
  font-size: 0.92rem;
  color: var(--vp-c-text-1);
}

.bsl-filter__count {
  flex-shrink: 0;
  font-family: var(--bsl-font-mono);
  font-size: 11.5px;
  color: var(--vp-c-text-3);
  white-space: nowrap;
}

.bsl-filter__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.bsl-filter__label {
  font-family: var(--bsl-font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--vp-c-text-3);
  margin-right: 2px;
}

.bsl-chip {
  padding: 5px 13px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 999px;
  background: var(--vp-c-bg);
  font-family: var(--bsl-font-display);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--vp-c-text-2);
  cursor: pointer;
  transition: border-color 0.18s, background-color 0.18s, color 0.18s;
}

.bsl-chip:hover {
  border-color: var(--vp-c-brand-3);
  color: var(--vp-c-text-1);
}

.bsl-chip.is-active {
  border-color: transparent;
  background: var(--bsl-gradient);
  color: #1a1206;
}

.bsl-chip--reset {
  border-style: dashed;
}

.bsl-filter__empty {
  margin: 16px 0 0;
  font-size: 0.9rem;
  color: var(--vp-c-text-3);
}

@media (max-width: 640px) {
  .bsl-filter__count {
    display: none;
  }
}
</style>
