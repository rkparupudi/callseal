package dev.callseal.common;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handle(ApiException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setTitle(e.title());
        return problem;
    }
}
