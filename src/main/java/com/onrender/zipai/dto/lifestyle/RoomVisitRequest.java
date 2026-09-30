package com.onrender.zipai.dto.lifestyle;

import java.time.LocalDate;
import java.time.LocalTime;

public class RoomVisitRequest {
    private String roomId;
    private String title;
    private LocalDate date;
    private LocalTime time;
    private String phone;
    private String question;

    public RoomVisitRequest() {
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
