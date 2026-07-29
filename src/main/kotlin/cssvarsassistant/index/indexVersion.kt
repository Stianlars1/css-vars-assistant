package cssvarsassistant.index

// Bump when either stylesheet index payload or import-resolution semantics
// change. 1048 rebuilds cached selector contexts and import closures for
// issues #28/#29 (`@use`/`@forward` and root/theme canonicalisation).
const val INDEX_VERSION = 1048
