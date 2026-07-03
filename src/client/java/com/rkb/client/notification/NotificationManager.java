package com.rkb.client.notification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {

    private final List<Notification> notifications = new ArrayList<>();

    public void push(String message, NotificationType type) {
        notifications.add(new Notification(message, type));
        System.out.println("[RKB] " + type + " : " + message);
    }

    public void update() {
        Iterator<Notification> iterator = notifications.iterator();

        while (iterator.hasNext()) {
            Notification n = iterator.next();

            if (System.currentTimeMillis() - n.getCreated() > 3000) {
                iterator.remove();
            }
        }
    }

    public List<Notification> getNotifications() {
        return notifications;
    }
}
