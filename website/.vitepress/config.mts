import { defineConfig } from 'vitepress'

const base = '/routerhub/'

// Modelled on keiyoushi.github.io: a VitePress site with a home page, guides, and an extension list
// that the browser fetches from the extensions repository's `repo` branch, so it is always current
// without rebuilding the site.
export default defineConfig({
  base,
  lang: 'en-US',
  title: 'RouterHub',
  description: 'Manage your home router from your phone. One Android app, extensions for every router family.',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}logo.svg` }],
    ['meta', { name: 'theme-color', content: '#0058CA' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'RouterHub' }],
    ['meta', { property: 'og:description', content: 'Manage your home router from your phone.' }],
  ],
  themeConfig: {
    logo: '/logo.svg',
    nav: [
      { text: 'Guide', link: '/docs/getting-started', activeMatch: '/docs/' },
      { text: 'Extensions', link: '/extensions/' },
      { text: 'Download', link: '/download' },
      { text: 'Changelog', link: 'https://github.com/atasanbratan/routerhub/releases' },
    ],
    sidebar: {
      '/docs/': [
        {
          text: 'Guides',
          items: [
            { text: 'Getting started', link: '/docs/getting-started' },
            { text: 'Extensions and trust', link: '/docs/extensions' },
            { text: 'Troubleshooting', link: '/docs/troubleshooting' },
          ],
        },
        {
          text: 'Contributing',
          items: [
            { text: 'Write an extension', link: 'https://github.com/atasanbratan/routerhub/blob/main/docs/writing-an-extension.md' },
            { text: 'Architecture', link: 'https://github.com/atasanbratan/routerhub/blob/main/docs/architecture.md' },
          ],
        },
      ],
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/atasanbratan/routerhub' },
    ],
    editLink: {
      pattern: 'https://github.com/atasanbratan/routerhub/edit/main/website/:path',
      text: 'Edit this page on GitHub',
    },
    search: { provider: 'local' },
    footer: {
      message: 'Released under the Apache 2.0 license. <a href="/routerhub/privacy">Privacy policy</a>',
      copyright: 'RouterHub contributors. Router brands belong to their owners; RouterHub is not affiliated with them.',
    },
  },
})
