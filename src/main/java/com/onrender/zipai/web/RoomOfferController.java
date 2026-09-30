package com.onrender.zipai.web;

import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.onrender.zipai.dto.lifestyle.ItemResponse;
import com.onrender.zipai.dto.lifestyle.ItemsResponse;
import com.onrender.zipai.dto.lifestyle.RoomOfferRequest;
import com.onrender.zipai.dto.lifestyle.RoomOfferResponse;
import com.onrender.zipai.service.RoomConnectService;

@RestController
@RequestMapping("/api/room-offers")
public class RoomOfferController {

    private final RoomConnectService roomConnectService;

    public RoomOfferController(RoomConnectService roomConnectService) {
        this.roomConnectService = roomConnectService;
    }

    @GetMapping
    public ItemsResponse<RoomOfferResponse> offers() {
        return new ItemsResponse<>(roomConnectService.getOffers());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ItemResponse<RoomOfferResponse> create(
            @RequestBody RoomOfferRequest request) {
        return new ItemResponse<>(roomConnectService.createOffer(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse<RoomOfferResponse> createWithImages(
            @RequestParam String title,
            @RequestParam String district,
            @RequestParam Long deposit,
            @RequestParam Long monthly,
            @RequestParam Long maintenance,
            @RequestParam LocalDate contractEnd,
            @RequestParam LocalDate moveIn,
            @RequestParam String availableTime,
            @RequestParam String agreement,
            @RequestParam(required = false) String description,
            @RequestParam(required = false, name = "images") MultipartFile[] images) {

        RoomOfferRequest request = new RoomOfferRequest();
        request.setTitle(title);
        request.setDistrict(district);
        request.setDeposit(deposit);
        request.setMonthly(monthly);
        request.setMaintenance(maintenance);
        request.setContractEnd(contractEnd);
        request.setMoveIn(moveIn);
        request.setAvailableTime(availableTime);
        request.setAgreement(agreement);
        request.setDescription(description);

        return new ItemResponse<>(roomConnectService.createOffer(request, images));
    }
}
