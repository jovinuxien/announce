package org.linlinjava.announce.messaging.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the messaging service, under the {@code messaging} prefix. */
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    /** How many characters a TEXT message may hold. */
    private int maxTextLength = 4000;
    /** How many messages one history page returns at most. */
    private int maxPageSize = 200;

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}
