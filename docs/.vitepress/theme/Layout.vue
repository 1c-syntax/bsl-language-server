<script setup lang="ts">
import { computed, nextTick, onMounted, watch } from 'vue'
import DefaultTheme from 'vitepress/theme'
import { useData, useRoute } from 'vitepress'
import BslHero from './components/BslHero.vue'
import BslHighlights from './components/BslHighlights.vue'
import BslDiagnosticsFilter from './components/BslDiagnosticsFilter.vue'
import { enableSortableTables } from './composables/sortableTables'

const { Layout } = DefaultTheme
const { frontmatter, page } = useData()
const route = useRoute()

const isHome = computed(() => frontmatter.value.heroPage === true)
const isDiagnosticsIndex = computed(
  () => page.value.relativePath === 'diagnostics/index.md' || page.value.relativePath === 'en/diagnostics/index.md',
)

function enhance() {
  nextTick(() => enableSortableTables())
}

onMounted(enhance)
watch(() => route.path, enhance)
</script>

<template>
  <Layout>
    <template #home-hero-before>
      <template v-if="isHome">
        <BslHero />
        <BslHighlights />
      </template>
    </template>

    <template #doc-before>
      <BslDiagnosticsFilter v-if="isDiagnosticsIndex" />
    </template>
  </Layout>
</template>
