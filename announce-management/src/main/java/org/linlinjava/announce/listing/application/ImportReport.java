package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.ListingGrade;
import org.linlinjava.announce.listing.domain.ListingQuality;

/**
 * What one import run did: {@code seen} items read, of which {@code inserted} were new, {@code updated} had changed
 * content (or came back from GONE), {@code unchanged} were only touched, {@code rejected} could not be normalised.
 * {@code good}, {@code weak} and {@code reject} count the grades of the inserted and updated rows;
 * {@code indexed} = the documents pushed to the index (good + weak when both grades are indexable, the default);
 * {@code indexFailed} = rows stored whose document could not be pushed after retries (the next reindex catches up).
 */
public record ImportReport(long seen, long inserted, long updated, long unchanged, long rejected,
                           long good, long weak, long reject, long indexed, long indexFailed) {

    public static final ImportReport EMPTY = new ImportReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** What happened to the document of a stored row. */
    enum IndexOutcome { INDEXED, SKIPPED, FAILED }

    ImportReport plus(ImportReport o) {
        return new ImportReport(seen + o.seen, inserted + o.inserted, updated + o.updated, unchanged + o.unchanged,
                rejected + o.rejected, good + o.good, weak + o.weak, reject + o.reject, indexed + o.indexed, indexFailed + o.indexFailed);
    }

    static ImportReport oneInserted(ListingQuality q, IndexOutcome index) {
        return new ImportReport(1, 1, 0, 0, 0, grade(q, ListingGrade.GOOD), grade(q, ListingGrade.WEAK), grade(q, ListingGrade.REJECT),
                index == IndexOutcome.INDEXED ? 1 : 0, index == IndexOutcome.FAILED ? 1 : 0);
    }

    static ImportReport oneUpdated(ListingQuality q, IndexOutcome index) {
        return new ImportReport(1, 0, 1, 0, 0, grade(q, ListingGrade.GOOD), grade(q, ListingGrade.WEAK), grade(q, ListingGrade.REJECT),
                index == IndexOutcome.INDEXED ? 1 : 0, index == IndexOutcome.FAILED ? 1 : 0);
    }

    static ImportReport oneUnchanged() { return new ImportReport(1, 0, 0, 1, 0, 0, 0, 0, 0, 0); }
    static ImportReport oneRejected() { return new ImportReport(1, 0, 0, 0, 1, 0, 0, 0, 0, 0); }

    private static long grade(ListingQuality q, ListingGrade g) {
        return q.is(g) ? 1 : 0;
    }
}
