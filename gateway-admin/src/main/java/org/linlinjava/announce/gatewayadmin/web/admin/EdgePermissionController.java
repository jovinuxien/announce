package org.linlinjava.announce.gatewayadmin.web.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** The catalogue of permission strings ({@code ROLE_SUPER}), for the role editor. */
@RestController
@RequestMapping("/api/admin/permissions")
public class EdgePermissionController {

    @GetMapping
    public Flux<PermissionCatalogue.Permission> all() {
        return Flux.fromIterable(PermissionCatalogue.ALL);
    }
}
