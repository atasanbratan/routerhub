<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { LATEST_RELEASE_API, RELEASES_PAGE } from '../constants'

// Asks GitHub for the latest release, like Keiyoushi's useMihonVersionQuery. Until it answers (or if
// there is no release yet) the button points at the releases page.
const version = ref<string | null>(null)
const apkUrl = ref(RELEASES_PAGE)
const size = ref<string | null>(null)
const failed = ref(false)

onMounted(async () => {
  try {
    const response = await fetch(LATEST_RELEASE_API, { headers: { Accept: 'application/vnd.github+json' } })
    if (!response.ok) throw new Error(String(response.status))
    const release = await response.json()
    version.value = release.tag_name
    const apk = (release.assets ?? []).find((a: { name: string }) => a.name.endsWith('.apk'))
    if (apk) {
      apkUrl.value = apk.browser_download_url
      size.value = `${(apk.size / 1024 / 1024).toFixed(1)} MB`
    }
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div class="download">
    <a class="download-button" :href="apkUrl">
      Download RouterHub<span v-if="version"> {{ version }}</span>
    </a>
    <p class="download-meta">
      <template v-if="version">Android 9 or newer<span v-if="size"> · {{ size }}</span> · </template>
      <template v-else-if="failed">No release found yet. </template>
      <a :href="RELEASES_PAGE">All releases and release notes</a>
    </p>
  </div>
</template>

<style scoped>
.download {
  text-align: center;
  margin: 24px 0;
}

.download-button {
  display: inline-block;
  border-radius: 20px;
  padding: 0 24px;
  line-height: 44px;
  font-weight: 600;
  color: var(--vp-button-brand-text);
  background-color: var(--vp-button-brand-bg);
  text-decoration: none !important;
  transition: background-color 0.25s;
}

.download-button:hover {
  background-color: var(--vp-button-brand-hover-bg);
}

.download-meta {
  font-size: 0.85rem;
  color: var(--vp-c-text-2);
}
</style>
