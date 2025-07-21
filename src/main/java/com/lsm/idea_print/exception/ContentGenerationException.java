package com.lsm.idea_print.exception;

public class ContentGenerationException extends McpException {
    public ContentGenerationException(String message) {
        super(message);
    }
    
    public ContentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}