package com.lsm.idea_print.exception;

public class PostingException extends McpException {
    public PostingException(String message) {
        super(message);
    }
    
    public PostingException(String message, Throwable cause) {
        super(message, cause);
    }
}