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
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/visits")
public class RoomVisitController {

    private final RoomConnectService roomConnectService;
    private final ZipaiAuthService auth;

    public RoomVisitController(RoomConnectService roomConnectService, ZipaiAuthService auth) {
        this.roomConnectService = roomConnectService;
        this.auth = auth;
    }

    @GetMapping
    public ItemsResponse<RoomVisitResponse> visits(HttpSession session) {
        Long userId = auth.required(session).getId();
        return new ItemsResponse<>(roomConnectService.getVisits(userId));
    }

    @GetMapping("/managed")
    public ItemsResponse<RoomVisitResponse> managedVisits(HttpSession session) {
        Long ownerUserId = auth.required(session).getId();
        return new ItemsResponse<>(roomConnectService.getManagedVisits(ownerUserId));
    }

    @GetMapping("/{visitId}")
    public ItemResponse<RoomVisitResponse> visit(
            @PathVariable Long visitId,
            HttpSession session) {
        Long userId = auth.required(session).getId();
        return new ItemResponse<>(roomConnectService.getVisit(userId, visitId));
    }

    @PostMapping
    public ItemResponse<RoomVisitResponse> create(
            @RequestBody RoomVisitRequest request,
            HttpSession session) {
        Long userId = auth.required(session).getId();
        return new ItemResponse<>(roomConnectService.createVisit(userId, request));
    }

    @PatchMapping("/{visitId}/approve")
    public ItemResponse<RoomVisitResponse> approve(
            @PathVariable Long visitId,
            HttpSession session) {
        Long ownerUserId = auth.required(session).getId();
        return new ItemResponse<>(roomConnectService.approveVisit(ownerUserId, visitId));
    }

    @PatchMapping("/{visitId}/reject")
    public ItemResponse<RoomVisitResponse> reject(
            @PathVariable Long visitId,
            HttpSession session) {
        Long ownerUserId = auth.required(session).getId();
        return new ItemResponse<>(roomConnectService.rejectVisit(ownerUserId, visitId));
    }
}
