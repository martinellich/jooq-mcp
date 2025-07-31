package ch.martinelli.jooqmcp.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.net.SocketTimeoutException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        logger.error("IO Exception occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Unable to fetch documentation. Please try again later.");
    }

    @ExceptionHandler(SocketTimeoutException.class)
    public ResponseEntity<String> handleTimeoutException(SocketTimeoutException e) {
        logger.error("Timeout exception occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body("Request timed out while fetching documentation. Please try again.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        logger.error("Unexpected error occurred: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred. Please try again later.");
    }
}