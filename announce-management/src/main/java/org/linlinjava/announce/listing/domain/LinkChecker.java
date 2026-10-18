package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Mono;

/** Probes whether a url still answers (HEAD, GET fallback); the real one is a WebClient, tests use a fake. Never errors. */
public interface LinkChecker {

    Mono<LinkStatus> check(String url);
}
