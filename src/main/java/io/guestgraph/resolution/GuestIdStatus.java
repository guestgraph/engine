package io.guestgraph.resolution;

/**
 * What a guest id refers to now. Reported from the outcome of the walk, not from the first hop: a
 * client asks one question — can I replace my stored id with one id? — and the status answers it.
 */
public enum GuestIdStatus {
  /** The id names a guest. */
  ACTIVE,
  /** Retired, and exactly one guest holds the person now. */
  MERGED,
  /** Retired, and several guests do. */
  SPLIT,
  /** Retired, and the walk found no current guest — an audit-trail inconsistency, reported. */
  RETIRED
}
