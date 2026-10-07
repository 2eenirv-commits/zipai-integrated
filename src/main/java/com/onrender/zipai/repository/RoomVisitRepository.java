package com.onrender.zipai.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.RoomVisit;

public interface RoomVisitRepository extends CrudRepository<RoomVisit, Long> {

    List<RoomVisit> findAllByRequesterUserIdOrderByVisitIdDesc(Long requesterUserId);

    List<RoomVisit> findAllByRoomIdInOrderByVisitIdDesc(Collection<String> roomIds);

    List<RoomVisit> findByRoomIdAndVisitDateAndVisitTimeAndStatus(
            String roomId,
            LocalDate visitDate,
            LocalTime visitTime,
            String status);
}
