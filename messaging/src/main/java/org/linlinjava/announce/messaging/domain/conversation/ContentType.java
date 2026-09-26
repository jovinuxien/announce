package org.linlinjava.announce.messaging.domain.conversation;

/** Kinds of message. */
public enum ContentType {
    TEXT,
    /** A picture by URL (no storage in this context). */
    IMAGE,
    /** A price proposal by the buyer of a LISTING conversation; the seller accepts or declines. */
    OFFER,
    /** Posted by the platform (order progress, staff actions); never by a member. */
    SYSTEM
}
