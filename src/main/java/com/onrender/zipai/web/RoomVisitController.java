package com.onrender.zipai.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onrender.zipai.dto.lifestyle.ItemResponse;
import com.onrender.zipai.dto.lifestyle.ItemsResponse;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.dto.lifestyle.RoomVisitResponse;
import com.onrender.zipai.service.RoomConnectService;

@RestController
@RequestMapping("/api/visits")
public class RoomVisitController {

    private final RoomConnectService roomConnectService;

    public RoomVisitController(RoomConnectService roomConnectService) {
        this.roomConnectService = roomConnectService;
    }

    @GetMapping
    public ItemsResponse<RoomVisitResponse> visits() {
        return new ItemsResponse<>(roomConnectService.getVisits());
    }

    @PostMapping
    public ItemResponse<RoomVisitResponse> create(
            @RequestBody RoomVisitRequest request) {
        return new ItemResponse<>(roomConnectService.createVisit(request));
    }

    @PatchMapping("/{visitId}/approve")
    public ItemResponse<RoomVisitResponse> approve(@PathVariable Long visitId) {
        return new ItemResponse<>(roomConnectService.approveVisit(visitId));
    }

    @PatchMapping("/{visitId}/reject")
    public ItemResponse<RoomVisitResponse> reject(@PathVariable Long visitId) {
        return new ItemResponse<>(roomConnectService.rejectVisit(visitId));
    }
}
