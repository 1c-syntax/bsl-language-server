import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import Layout from './Layout.vue'

import './styles/fonts.css'
import './styles/vars.css'
import './styles/base.css'

export default {
  extends: DefaultTheme,
  Layout,
} satisfies Theme
