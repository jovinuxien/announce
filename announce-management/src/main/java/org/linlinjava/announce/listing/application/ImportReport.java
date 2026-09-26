package org.linlinjava.announce.listing.application;

/**
 * What one import run did: {@code seen} items read, of which {@code inserted} were new, {@code updated} had changed
 * content (or came back from GONE), {@code unchanged} were only touched, {@code rejected} could not be normalised.
 * {@code indexed} = inserted + updated: the number of documents pushed to the search index.
 */
public record ImportReport(long seen, long inserted, long updated, long unchanged, long rejected) {

    public static final ImportReport EMPTY = new ImportReport(0, 0, 0, 0, 0);

    public long indexed() {
        return inserted + updated;
    }

    ImportReport plus(ImportReport o) {
        return new ImportReport(seen + o.seen, inserted + o.inserted, updated + o.updated, unchanged + o.unchanged, rejected + o.rejected);
    }

    static ImportReport oneInserted() { return new ImportReport(1, 1, 0, 0, 0); }
    static ImportReport oneUpdated() { return new ImportReport(1, 0, 1, 0, 0); }
    static ImportReport oneUnchanged() { return new ImportReport(1, 0, 0, 1, 0); }
    static ImportReport oneRejected() { return new ImportReport(1, 0, 0, 0, 1); }
}
