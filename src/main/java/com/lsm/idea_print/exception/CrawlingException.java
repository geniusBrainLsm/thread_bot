package com.lsm.idea_print.exception;

public class CrawlingException extends McpException {
    public CrawlingException(String message) {
        super(message);
    }
    
    public CrawlingException(String message, Throwable cause) {
        super(message, cause);
    }
}