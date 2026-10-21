package akyrion.thepredict.domain;

/**
 * One difficulty observation carried by a fight digest (M27.3). Absent when a group is below fair.
 *
 * @param lnTtk natural log of the time to kill
 * @param startRungEstimate rung estimate {@code u}
 */
public record DifficultySample(float lnTtk, float startRungEstimate) {
}
