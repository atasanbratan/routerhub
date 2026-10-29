<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { EXTENSIONS_INDEX, EXTENSIONS_RAW, EXTENSIONS_REPO } from '../constants'

// The shape written by publish-repo.py in the extensions repository.
interface Extension {
  name: string
  packageName: string
  versionCode: number
  versionName: string
  apiVersion: number
  apk: string
  size: number
  sha256: string
}

interface Index {
  name: string
  signingKey: string
  extensions: Extension[]
}

const index = ref<Index | null>(null)
const error = ref<string | null>(null)
const search = ref('')

onMounted(async () => {
  try {
    const response = await fetch(EXTENSIONS_INDEX, { cache: 'no-cache' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    index.value = await response.json()
    // Like Keiyoushi: jump to the extension a shared link points at.
    if (location.hash) {
      requestAnimationFrame(() => document.getElementById(location.hash.slice(1))?.scrollIntoView({ behavior: 'smooth' }))
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

const shown = computed(() => {
  const query = search.value.trim().toLowerCase()
  const all = index.value?.extensions ?? []
  return query ? all.filter((e) => e.name.toLowerCase().includes(query) || e.packageName.includes(query)) : all
})

const fingerprint = computed(() => index.value?.signingKey.match(/.{2}/g)?.join(':').toUpperCase() ?? '')

function shortId(packageName: string) {
  return packageName.replace('dev.routerhub.extension.', '')
}

function kilobytes(bytes: number) {
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}
</script>

<template>
  <div class="extensions">
    <input v-model="search" class="extension-search" type="search" placeholder="Search by name or package" aria-label="Search extensions">

    <p v-if="error" class="custom-block warning extension-state">
      The extension list could not be loaded ({{ error }}). Browse the
      <a :href="`https://github.com/${EXTENSIONS_REPO}/tree/repo/apk`">APK folder</a> instead.
    </p>
    <p v-else-if="!index" class="extension-state">Loading extensions…</p>
    <template v-else>
      <p class="extension-count">
        {{ shown.length }} of {{ index.extensions.length }} extensions
      </p>
      <div v-for="ext in shown" :id="shortId(ext.packageName)" :key="ext.packageName" class="extension" tabindex="-1">
        <a :href="`#${shortId(ext.packageName)}`" class="anchor" aria-hidden="true">#</a>
        <svg class="extension-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20.2,5.9l0.8,-0.8C19.6,3.7 17.8,3 16,3s-3.6,0.7 -5,2.1l0.8,0.8C13,4.8 14.5,4.2 16,4.2s3,0.6 4.2,1.7zM19.4,6.7c-0.9,-0.9 -2.1,-1.4 -3.4,-1.4s-2.5,0.5 -3.4,1.4l0.8,0.8c0.7,-0.7 1.6,-1 2.6,-1 0.9,0 1.9,0.4 2.6,1l0.8,-0.8zM19,13h-2L17,9h-2v4L5,13c-1.1,0 -2,0.9 -2,2v4c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2v-4c0,-1.1 -0.9,-2 -2,-2zM8,18L6,18v-2h2v2zM11.5,18h-2v-2h2v2zM15,18h-2v-2h2v2z" />
        </svg>
        <div class="extension-text">
          <div class="upper">{{ ext.name }}</div>
          <div class="lower">{{ shortId(ext.packageName) }} · {{ kilobytes(ext.size) }}</div>
        </div>
        <Badge type="info" :text="`API ${ext.apiVersion}`" title="The router-api version this extension needs" />
        <Badge type="tip" :text="`v${ext.versionName}`" />
        <a :href="EXTENSIONS_RAW + ext.apk" class="extension-download" :title="`Download ${ext.name} (SHA-256 ${ext.sha256})`">↓</a>
      </div>
      <p class="extension-signer">
        Every APK here is signed with the certificate <code>{{ fingerprint }}</code>, which RouterHub shows as
        <strong>Official</strong>.
      </p>
    </template>
  </div>
</template>

<style scoped>
.extension-search {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
  font-size: 0.95rem;
}

.extension-search:focus {
  border-color: var(--vp-c-brand-1);
  outline: none;
}

.extension-count,
.extension-state {
  color: var(--vp-c-text-2);
  font-size: 0.85rem;
}

.extension {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.675rem;
  padding: 0.5em;
  margin: 0.6em -0.5em;
  border-radius: 8px;
  scroll-margin-top: calc(var(--vp-nav-height) + 24px);
}

.extension:hover {
  background-color: var(--vp-c-bg-soft);
}

.extension:target,
.extension:focus {
  outline: none;
  background-color: var(--vp-c-brand-soft);
  transition: 500ms background-color;
}

.anchor {
  position: absolute;
  left: -1em;
  font-size: 1.3em;
  opacity: 0;
  text-decoration: none !important;
}

.extension:hover .anchor {
  opacity: 1;
}

.extension-icon {
  width: 42px;
  height: 42px;
  padding: 9px;
  flex-shrink: 0;
  border-radius: 10px;
  fill: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
}

.extension-text {
  flex-grow: 1;
  min-width: 0;
}

.upper {
  font-weight: 600;
  color: var(--vp-c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lower {
  font-family: var(--vp-font-family-mono);
  font-size: 0.75rem;
  line-height: 1rem;
  color: var(--vp-c-text-2);
}

.extension :deep(.VPBadge) {
  flex-shrink: 0;
  margin: 0;
  white-space: nowrap;
}

.extension-download {
  flex-shrink: 0;
  padding: 0.2em 0.4em;
  font-size: 1.4em;
  font-weight: 700;
  text-decoration: none !important;
}

.extension-signer {
  margin-top: 24px;
  font-size: 0.8rem;
  color: var(--vp-c-text-2);
  overflow-wrap: anywhere;
}

@media (max-width: 767px) {
  .anchor {
    display: none;
  }

  .extension {
    margin: 0.6em 0;
    border: 1px solid var(--vp-c-divider);
  }

  .extension :deep(.VPBadge.info) {
    display: none;
  }
}
</style>
