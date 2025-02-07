package org.smartregister.addo.domain;

public class VisitSubmissionResult {
    private static VisitSubmissionResult instance;
    private String eventId;

    private VisitSubmissionResult() {} // Private Constructor

    public static VisitSubmissionResult getInstance() {
        if (instance == null) {
            instance = new VisitSubmissionResult();
        }
        return instance;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}
