package es.bercianor.tocacorrer.data.export

/**
 * Controls how GPS points are segmented in the exported GPX file.
 *
 * - NONE: All points in a single <trkseg> within a single <trk> (default behaviour).
 * - TRACKS: Consecutive same-phase points are grouped into separate <trk> elements,
 *           each with a <name> tag formatted as "{1-based index}. {PhaseName}".
 * - SEGMENTS: Consecutive same-phase points are grouped into separate <trkseg>
 *             elements within a single <trk>. No <name> is added to <trkseg>
 *             (GPX 1.1 does not support it).
 */
enum class GpxSegmentationMode { NONE, TRACKS, SEGMENTS }
