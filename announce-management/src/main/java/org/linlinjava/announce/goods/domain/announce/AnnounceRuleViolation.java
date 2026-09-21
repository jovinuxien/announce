package org.linlinjava.announce.goods.domain.announce;

/** A business rule of the announce catalog was violated (wrong lifecycle transition, invariant broken). */
public class AnnounceRuleViolation extends RuntimeException {
    public AnnounceRuleViolation(String message) {
        super(message);
    }
}
