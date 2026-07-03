package com.rkb.client.notification;

public class Notification {

    private final String message;
    private final NotificationType type;
    private final long created;

    public Notification(String message, NotificationType type) {
        this.message = message;
        this.type = type;
        this.created = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public NotificationType getType() {
        return type;
    }

    public long getCreated() {
        return created;
    }
}
