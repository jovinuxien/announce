package org.linlinjava.announce.listing.interfaces.rest;

import org.linlinjava.announce.listing.application.ListingGoneException;
import org.linlinjava.announce.listing.application.ListingNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

/** RFC 7807 problems for the listing endpoints: 404 unknown, 410 gone, 400 bad input. */
@RestControllerAdvice(basePackages = "org.linlinjava.announce.listing.interfaces.rest")
public class ListingExceptionHandler {

    @ExceptionHandler(ListingNotFoundException.class)
    public ProblemDetail notFound(ListingNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ListingGoneException.class)
    public ProblemDetail gone(ListingGoneException e) {
        return problem(HttpStatus.GONE, e);
    }

    @ExceptionHandler({IllegalArgumentException.class, ServerWebInputException.class})
    public ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    private static ProblemDetail problem(HttpStatus status, Exception e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setTitle(status.getReasonPhrase());
        return p;
    }
}
