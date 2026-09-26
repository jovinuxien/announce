package org.linlinjava.announce.messaging.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What this context needs to know about an announce: who owns it. Resolved through {@link AnnounceLookup}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnounceRef(String id, Integer sellerId, String title, String status) {
}
