package org.linlinjava.announce.cqrs.sample;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

public final class ListingCommands {

    public static final String AGGREGATE = "listing";

    private ListingCommands() {
    }

    public record CreateListing(String id, String title, long priceCents) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.PRISTINE;
        }
    }

    public record ChangeListingPrice(String id, long priceCents) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }

    public record CloseListing(String id) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }
}
