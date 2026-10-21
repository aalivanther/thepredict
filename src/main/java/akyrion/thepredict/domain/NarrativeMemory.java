package akyrion.thepredict.domain;

/**
 * A profile hash and the day it was last the killer or the victim (M27.1). Inspect is the only reader.
 *
 * @param hash FNV-1a of the profile name
 * @param epochDay civil day
 */
public record NarrativeMemory(int hash, long epochDay) {
}
