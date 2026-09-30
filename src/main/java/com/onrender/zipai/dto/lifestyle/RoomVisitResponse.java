package com.onrender.zipai.dto.lifestyle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.onrender.zipai.domain.RoomVisit;

public class RoomVisitResponse {
    private final Long id;
    private final String roomId;
    private final String title;
    private final LocalDate date;
    private final String time;
    private final String phone;
    private final String question;
    private final String status;

    public RoomVisitResponse(
            Long id,
            String roomId,
            String title,
            LocalDate date,
            String time,
            String phone,
            String question,
            String status) {
        this.id = id;
        this.roomId = roomId;
        this.title = title;
        this.date = date;
        this.time = time;
        this.phone = phone;
        this.question = question;
        this.status = status;
    }

    public static RoomVisitResponse from(RoomVisit visit) {
        return new RoomVisitResponse(
                visit.getVisitId(),
                visit.getRoomId(),
                visit.getTitle(),
                visit.getVisitDate(),
                visit.getVisitTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                visit.getPhone(),
                visit.getQuestion(),
                visit.getStatus());
    }

    public Long getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getTitle() { return title; }
    public LocalDate getDate() { return date; }
    public String getTime() { return time; }
    public String getPhone() { return phone; }
    public String getQuestion() { return question; }
    public String getStatus() { return status; }
}
