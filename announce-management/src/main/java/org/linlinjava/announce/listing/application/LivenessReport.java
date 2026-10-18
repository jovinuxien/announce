package org.linlinjava.announce.listing.application;

/**
 * What one liveness pass did: {@code checked} rows probed, {@code gone} set GONE (404/410 on the url), {@code imageDead}
 * rows whose image stopped answering (downgraded), {@code imageBack} rows whose image answers again, {@code reindexed}
 * documents pushed or removed because a verdict changed the grade.
 */
public record LivenessReport(long checked, long gone, long imageDead, long imageBack, long reindexed) {

    public static final LivenessReport EMPTY = new LivenessReport(0, 0, 0, 0, 0);

    LivenessReport plus(LivenessReport o) {
        return new LivenessReport(checked + o.checked, gone + o.gone, imageDead + o.imageDead, imageBack + o.imageBack, reindexed + o.reindexed);
    }
}
