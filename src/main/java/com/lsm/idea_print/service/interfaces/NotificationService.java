package com.lsm.idea_print.service.interfaces;

public interface NotificationService {
    void notifySuccess(String articleTitle);
    void notifyFailure(String articleTitle, String errorMessage);
    void notifyNoCrawledContent();
    void notifyDuplicateSkipped(String articleTitle);
}