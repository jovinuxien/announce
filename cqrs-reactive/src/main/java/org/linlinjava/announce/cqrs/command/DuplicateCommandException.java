package org.linlinjava.announce.cqrs.command;

/**
 * A command with an already seen {@code commandId} was dispatched again and its result could not be
 * replayed (no result type given, or the first dispatch is still in flight).
 */
public class DuplicateCommandException extends RuntimeException {

    private final String commandId;
    private final String subject;
    private final boolean inFlight;

    public DuplicateCommandException(String commandId, String subject, boolean inFlight) {
        super("Command " + commandId + " on " + subject + (inFlight ? " is still being handled" : " was already handled"));
        this.commandId = commandId;
        this.subject = subject;
        this.inFlight = inFlight;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getSubject() {
        return subject;
    }

    public boolean isInFlight() {
        return inFlight;
    }
}
