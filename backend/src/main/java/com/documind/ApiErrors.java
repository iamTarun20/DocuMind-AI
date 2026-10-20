package com.documind;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> application(ApiException e) {
        return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> oversized() {
        return ResponseEntity.status(413).body(Map.of("message", "The file exceeds the 10 MB upload limit."));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class, org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request. Check the file, session, consent and answer selections."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        // Never return provider payloads, credentials or uploaded document content.
        return ResponseEntity.internalServerError().body(Map.of("message", "The request could not be completed. Please try again."));
    }
}
