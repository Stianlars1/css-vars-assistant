package cssvarsassistant.index

// Bumped for 1.8.0: the CSS variable parser now strips inline `/* ... */`
// comments from values and handles leading block comments on the same line
// as a declaration, so indexed values and `lastComment` attribution differ
// from 1.7.2. Forcing a re-index prevents stale entries from leaking into
// completion/hover.
const val INDEX_VERSION = 1044
