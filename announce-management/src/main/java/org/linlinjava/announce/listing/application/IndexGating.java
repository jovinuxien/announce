package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.ListingGrade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which grades reach the search index: {@code announce.listing.index-grades}, default GOOD and WEAK. REJECT rows stay
 * in the table with their issues and are never indexed; a row without a grade (imported before grading existed) is
 * graded on the next reindex.
 */
@Component
public class IndexGating {

    private final Set<ListingGrade> grades;

    public IndexGating(@Value("${announce.listing.index-grades:GOOD,WEAK}") Set<ListingGrade> grades) {
        this.grades = grades.isEmpty() ? EnumSet.noneOf(ListingGrade.class) : EnumSet.copyOf(grades);
    }

    public Set<ListingGrade> grades() {
        return grades;
    }

    /** True when an ACTIVE listing of this grade belongs in the index. */
    public boolean indexable(CrawledListing l) {
        return l.isActive() && l.quality() != null && grades.contains(l.quality().grade());
    }
}
