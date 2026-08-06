<script setup lang="ts">
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import BslEditorPreview from './BslEditorPreview.vue'

const { lang, theme } = useData()
const isRu = computed(() => lang.value.startsWith('ru'))
const root = computed(() => (isRu.value ? '/' : '/en/'))

const copy = computed(() =>
  isRu.value
    ? {
        eyebrow: 'Language Server Protocol · 1С:Предприятие 8 · OneScript',
        title: 'Статический анализ и IDE-возможности для кода на 1С',
        lead:
          'Один языковой сервер, который подключается к любому редактору с поддержкой LSP, ' +
          'находит ошибки и нарушения стандартов и умеет работать в пакетном режиме на CI.',
        primary: 'Возможности',
        secondary: 'Каталог диагностик',
        tertiary: 'GitHub',
        stats: [
          { value: String(theme.value.diagnosticsCount ?? 186), label: 'диагностик' },
          { value: '23', label: 'возможности LSP' },
          { value: '2', label: 'языка интерфейса' },
          { value: 'Java 21+', label: 'рантайм' },
        ],
      }
    : {
        eyebrow: 'Language Server Protocol · 1C:Enterprise 8 · OneScript',
        title: 'Static analysis and IDE features for 1C code',
        lead:
          'A single language server that plugs into any LSP-capable editor, finds bugs and ' +
          'coding standard violations, and runs headless in your CI pipeline.',
        primary: 'Capabilities',
        secondary: 'Diagnostics catalog',
        tertiary: 'GitHub',
        stats: [
          { value: String(theme.value.diagnosticsCount ?? 186), label: 'diagnostics' },
          { value: '23', label: 'LSP capabilities' },
          { value: '2', label: 'UI languages' },
          { value: 'Java 21+', label: 'runtime' },
        ],
      },
)
</script>

<template>
  <section class="bsl-hero">
    <div class="bsl-hero__backdrop" aria-hidden="true">
      <div class="bsl-hero__grid" />
      <div class="bsl-hero__glow bsl-hero__glow--a" />
      <div class="bsl-hero__glow bsl-hero__glow--b" />
    </div>

    <div class="bsl-hero__inner">
      <div class="bsl-hero__copy">
        <p class="bsl-hero__eyebrow">{{ copy.eyebrow }}</p>
        <h1 class="bsl-hero__title">
          <span class="bsl-hero__mark">BSL Language Server</span>
          <span class="bsl-hero__tagline">{{ copy.title }}</span>
        </h1>
        <p class="bsl-hero__lead">{{ copy.lead }}</p>

        <div class="bsl-hero__actions">
          <a class="bsl-btn bsl-btn--primary" :href="withBase(root + 'capabilities/')">
            {{ copy.primary }}
          </a>
          <a class="bsl-btn" :href="withBase(root + 'diagnostics/')">{{ copy.secondary }}</a>
          <a
            class="bsl-btn bsl-btn--ghost"
            href="https://github.com/1c-syntax/bsl-language-server"
            target="_blank"
            rel="noreferrer"
          >
            {{ copy.tertiary }}
          </a>
        </div>

        <dl class="bsl-hero__stats">
          <div v-for="stat in copy.stats" :key="stat.label" class="bsl-hero__stat">
            <dt>{{ stat.value }}</dt>
            <dd>{{ stat.label }}</dd>
          </div>
        </dl>
      </div>

      <div class="bsl-hero__visual">
        <BslEditorPreview />
      </div>
    </div>
  </section>
</template>

<style scoped>
.bsl-hero {
  position: relative;
  overflow: hidden;
  padding: 72px 24px 64px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.bsl-hero__backdrop {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bsl-hero__grid {
  position: absolute;
  inset: -1px;
  background-image: linear-gradient(var(--bsl-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--bsl-grid) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(120% 90% at 50% 0%, #000 25%, transparent 78%);
  -webkit-mask-image: radial-gradient(120% 90% at 50% 0%, #000 25%, transparent 78%);
}

.bsl-hero__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}

.bsl-hero__glow--a {
  top: -180px;
  left: -80px;
  width: 520px;
  height: 420px;
  background: radial-gradient(circle, rgba(255, 200, 87, 0.55), transparent 68%);
}

.bsl-hero__glow--b {
  top: -120px;
  right: -120px;
  width: 560px;
  height: 460px;
  background: radial-gradient(circle, rgba(255, 107, 91, 0.4), transparent 68%);
}

.dark .bsl-hero__glow {
  opacity: 0.35;
}

.bsl-hero__inner {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 48px;
  max-width: 1320px;
  margin: 0 auto;
}

@media (min-width: 1024px) {
  .bsl-hero {
    padding: 104px 32px 88px;
  }

  .bsl-hero__inner {
    grid-template-columns: minmax(0, 1.02fr) minmax(0, 1fr);
    align-items: center;
    gap: 64px;
  }
}

.bsl-hero__eyebrow {
  display: inline-block;
  margin: 0 0 22px;
  padding: 6px 14px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 999px;
  background: var(--bsl-surface);
  font-family: var(--bsl-font-mono);
  font-size: 11.5px;
  letter-spacing: 0.02em;
  color: var(--vp-c-text-2);
  box-shadow: var(--bsl-shadow);
}

.bsl-hero__title {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  border: none;
}

.bsl-hero__mark {
  font-family: var(--bsl-font-hero);
  font-weight: 700;
  font-size: clamp(1.9rem, 1rem + 3.6vw, 3.35rem);
  line-height: 1.04;
  letter-spacing: -0.045em;
  background: var(--bsl-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.bsl-hero__tagline {
  font-family: var(--bsl-font-display);
  font-weight: 700;
  font-size: clamp(1.1rem, 0.85rem + 0.9vw, 1.55rem);
  line-height: 1.28;
  letter-spacing: -0.025em;
  color: var(--vp-c-text-1);
  max-width: 22ch;
}

.bsl-hero__lead {
  margin: 22px 0 0;
  max-width: 54ch;
  font-size: 1.02rem;
  line-height: 1.7;
  color: var(--vp-c-text-2);
}

.bsl-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
}

.bsl-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  padding: 0 24px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 999px;
  background: var(--bsl-surface);
  font-family: var(--bsl-font-display);
  font-weight: 700;
  font-size: 0.92rem;
  color: var(--vp-c-text-1);
  text-decoration: none;
  transition: transform 0.18s, border-color 0.18s, box-shadow 0.18s, background-color 0.18s;
}

.bsl-btn:hover {
  transform: translateY(-2px);
  border-color: var(--vp-c-brand-3);
  box-shadow: var(--bsl-shadow);
}

.bsl-btn--primary {
  border-color: transparent;
  background: var(--bsl-gradient);
  color: #1a1206;
}

.bsl-btn--primary:hover {
  border-color: transparent;
  box-shadow: 0 14px 34px -14px rgba(240, 147, 43, 0.9);
}

.bsl-btn--ghost {
  background: transparent;
}

.bsl-hero__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 32px;
  margin: 46px 0 0;
  padding-top: 30px;
  border-top: 1px solid var(--vp-c-divider);
}

@media (min-width: 640px) {
  .bsl-hero__stats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.bsl-hero__stat dt {
  font-family: var(--bsl-font-display);
  font-weight: 800;
  font-size: 1.55rem;
  letter-spacing: -0.03em;
  color: var(--vp-c-text-1);
}

.bsl-hero__stat dd {
  margin: 4px 0 0;
  font-size: 0.8rem;
  line-height: 1.35;
  color: var(--vp-c-text-3);
}

.bsl-hero__visual {
  min-width: 0;
}
</style>
