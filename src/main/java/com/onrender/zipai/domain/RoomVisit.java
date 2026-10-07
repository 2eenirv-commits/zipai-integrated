package com.onrender.zipai.domain;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("room_visit")
public class RoomVisit {

    @Id
    @Column("visit_id")
    private Long visitId;

    @Column("requester_user_id")
    private Long requesterUserId;

    @Column("room_id")
    private String roomId;

    private String title;

    @Column("visit_date")
    private LocalDate visitDate;

    @Column("visit_time")
    private LocalTime visitTime;

    private String phone;
    private String question;
    private String status;

    public RoomVisit() {
    }

    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }
    public Long getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(Long requesterUserId) { this.requesterUserId = requesterUserId; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
    public LocalTime getVisitTime() { return visitTime; }
    public void setVisitTime(LocalTime visitTime) { this.visitTime = visitTime; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
