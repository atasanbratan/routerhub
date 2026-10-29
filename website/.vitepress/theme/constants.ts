export const APP_REPO = 'atasanbratan/routerhub'
export const EXTENSIONS_REPO = 'atasanbratan/routerhub-extensions'

/**
 * Written by the extensions repository's Publish workflow (.github/scripts/publish-repo.py). Set
 * VITE_EXTENSIONS_RAW to try the site against another copy, such as a local test of that script.
 */
export const EXTENSIONS_RAW: string =
  import.meta.env.VITE_EXTENSIONS_RAW ?? `https://raw.githubusercontent.com/${EXTENSIONS_REPO}/repo/`
export const EXTENSIONS_INDEX = `${EXTENSIONS_RAW}index.min.json`

export const LATEST_RELEASE_API = `https://api.github.com/repos/${APP_REPO}/releases/latest`
export const RELEASES_PAGE = `https://github.com/${APP_REPO}/releases`
