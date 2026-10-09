package ch.martinelli.jooqmcp.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.SocketTimeoutException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Unknown paths are a routine event on a public server - MCP clients configured for
     * the retired /sse endpoint hit this constantly. Answer 404 and keep the stack trace
     * out of the log; the generic handler below would otherwise report it as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleNoResourceFound(NoResourceFoundException e) {
        logger.debug("No handler for request: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Not found. The MCP endpoint of this server is /mcp.");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        // Usually a client that hung up mid-response - log the cause, not the stack.
        logger.warn("IO Exception occurred: {}", e.getMessage());
        logger.debug("IO Exception details", e);
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