package org.linlinjava.announce.listing.interfaces.rest;

import org.linlinjava.announce.listing.application.ListingClickOutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Click-out to the source page: {@code GET /out/{id}} counts the click and answers 302 to the listing's url,
 * 410 when the listing is GONE, 404 when the id is unknown. The frontend shows {@code external_url} as a domain
 * only and sends every click through here.
 */
@RestController
public class ListingClickOutController {

    private final ListingClickOutService clickOut;

    public ListingClickOutController(ListingClickOutService clickOut) {
        this.clickOut = clickOut;
    }

    @GetMapping("/out/{id}")
    public Mono<ResponseEntity<Void>> out(@PathVariable String id) {
        return clickOut.clickOut(id)
                .map(l -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(l.url())).<Void>build());
    }
}
