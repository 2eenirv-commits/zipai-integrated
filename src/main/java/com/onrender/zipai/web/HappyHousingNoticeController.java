package com.onrender.zipai.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onrender.zipai.dto.happyhousing.notice.CrawlHistoryResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeDetailResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeRuleResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeSummaryResponse;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeMatchRequest;
import com.onrender.zipai.dto.happyhousing.notice.HousingNoticeMatchResponse;
import com.onrender.zipai.service.HappyHousingNoticeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/happy-housing")
public class HappyHousingNoticeController {

    private final HappyHousingNoticeService happyHousingNoticeService;

    @GetMapping("/notices")
    public List<HousingNoticeSummaryResponse> notices(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String housingType,
            @RequestParam(required = false) String applicantType) {

        return happyHousingNoticeService.getNotices(query, region, status, housingType, applicantType);
    }

    @GetMapping("/notices/{noticeId}")
    public HousingNoticeDetailResponse notice(@PathVariable Long noticeId) {
        return happyHousingNoticeService.getNotice(noticeId);
    }

    @GetMapping("/notices/pan/{panId}")
    public HousingNoticeDetailResponse noticeByPanId(@PathVariable String panId) {
        return happyHousingNoticeService.getNoticeByPanId(panId);
    }

    @GetMapping("/notices/{noticeId}/rules")
    public List<HousingNoticeRuleResponse> rules(
            @PathVariable Long noticeId,
            @RequestParam(required = false) String applicantType) {

        return happyHousingNoticeService.getRules(noticeId, applicantType);
    }


    @PostMapping("/matches")
    public List<HousingNoticeMatchResponse> matches(
            @RequestBody HousingNoticeMatchRequest request) {
        return happyHousingNoticeService.matchNotices(request);
    }

    @GetMapping("/crawl-history/latest")
    public CrawlHistoryResponse latestCrawlHistory() {
        return happyHousingNoticeService.getLatestCrawlHistory();
    }
}
