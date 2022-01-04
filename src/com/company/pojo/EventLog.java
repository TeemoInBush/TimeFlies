/*
 * *****************************************************
 * Copyright (C) 2021 bytedance.com. All Rights Reserved
 * This file is part of bytedance EA project.
 * Unauthorized copy of this file, via any medium is strictly prohibited.
 * Proprietary and Confidential.
 * ****************************************************
 */
package com.company.pojo;

import com.company.pojo.enums.EventType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间日志
 *
 * @author lixinxing.world
 * @date 12/07/2021
 **/
public class EventLog implements Serializable {

    private static final long serialVersionUID = 1;

    private LocalDateTime time;

    private EventType type;

    private String eventName;

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public static EventLog end(String eventName) {
        EventLog event = new EventLog();
        event.setEventName(eventName);
        event.setType(EventType.END);
        event.setTime(LocalDateTime.now());
        return event;
    }

    public EventLog(String eventName, EventType type, LocalDateTime time) {
        this.time = time;
        this.type = type;
        this.eventName = eventName;
    }

    public EventLog() {
    }

    //    public static EventLog suspend(String eventName) {
//        EventLog event = new EventLog();
//        event.setEventName(eventName);
//        event.setType(EventType.SUSPEND);
//        event.setTime(LocalDateTime.now());
//        return event;
//    }

    public static EventLog start(String eventName) {
        EventLog event = new EventLog();
        event.setEventName(eventName);
        event.setType(EventType.START);
        event.setTime(LocalDateTime.now());
        return event;
    }

    @Override
    public String toString() {
        return "[" + format(time) + "] " + type + " " + eventName;
    }

    public String format(LocalDateTime time) {
        return time.format(DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss"));
    }

    public String print() {
        return " | " + type + " " + eventName;
    }
}