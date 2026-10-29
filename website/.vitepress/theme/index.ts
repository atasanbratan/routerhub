import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import DownloadButton from './components/DownloadButton.vue'
import ExtensionList from './components/ExtensionList.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('DownloadButton', DownloadButton)
    app.component('ExtensionList', ExtensionList)
  },
} satisfies Theme
