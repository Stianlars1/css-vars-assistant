package cssvarsassistant.index

// Bumped for 1.8.1: the packed index record now carries a 4th field (1-based
// source line) and blocks opened by non-root selectors (`[data-theme=dark]`,
// `.dark`, …) push their own context label instead of collapsing into
// `default`. Existing caches are 3-part and their selector blocks were
// silently merged, so forcing a re-index surfaces the new data immediately.
const val INDEX_VERSION = 1045
