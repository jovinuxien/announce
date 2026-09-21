package org.linlinjava.announce.goods.interfaces.rest;

import org.linlinjava.announce.goods.application.announce.AnnounceNotFoundException;
import org.linlinjava.announce.goods.application.announce.NotAnnounceOwnerException;
import org.linlinjava.announce.goods.application.brand.BrandNotFoundException;
import org.linlinjava.announce.goods.application.category.CategoryNotFoundException;
import org.linlinjava.announce.goods.domain.announce.AnnounceRuleViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebInputException;

/** Maps catalog exceptions to RFC 7807 problem responses. */
@RestControllerAdvice(basePackages = "org.linlinjava.announce.goods.interfaces.rest")
public class CatalogExceptionHandler {

    @ExceptionHandler({AnnounceNotFoundException.class, CategoryNotFoundException.class, BrandNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(NotAnnounceOwnerException.class)
    public ProblemDetail forbidden(NotAnnounceOwnerException e) {
        return problem(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(AnnounceRuleViolation.class)
    public ProblemDetail conflict(AnnounceRuleViolation e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingRequestValueException.class, ServerWebInputException.class, WebExchangeBindException.class})
    public ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    private static ProblemDetail problem(HttpStatus status, Exception e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setTitle(status.getReasonPhrase());
        return p;
    }
}
