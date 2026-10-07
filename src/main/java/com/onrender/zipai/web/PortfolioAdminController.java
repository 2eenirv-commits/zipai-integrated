package com.onrender.zipai.web;

import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/admin")
public class PortfolioAdminController {
    private final ZipaiAuthService auth;

    public PortfolioAdminController(ZipaiAuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpSession session) {
        requireGuest(session);
        return Map.of(
            "newMembers", 4,
            "activeMembers", 128,
            "newInquiries", 2,
            "unansweredInquiries", 3,
            "pendingProperties", 2,
            "pendingVisits", 2
        );
    }

    @GetMapping("/inquiries")
    public Map<String, Object> inquiries(HttpSession session) {
        requireGuest(session);
        return Map.of("items", List.of(
            Map.of("id", 9001, "category", "매물 이용", "title", "방문 가능 시간 문의",
                "email", "g***@example.com", "message", "개인정보를 제거한 포트폴리오용 문의 예시입니다.",
                "username", "게스트***", "status", "received", "answer", "", "createdAt", "2026-10-06T10:20:00"),
            Map.of("id", 9002, "category", "계약 안전", "title", "체크리스트 이용 문의",
                "email", "d***@example.com", "message", "실제 문의 내용 대신 비식별 문구를 표시합니다.",
                "username", "이용자***", "status", "answered", "answer", "확인 방법을 안내한 데모 답변입니다.",
                "createdAt", "2026-10-05T15:40:00")
        ));
    }

    @GetMapping("/properties")
    public Map<String, Object> properties(HttpSession session) {
        requireGuest(session);
        return Map.of("items", List.of(
            Map.of("id", 8001, "owner", "등록자***", "title", "역세권 원룸 데모",
                "address", "서울특별시 강남구 ***", "dealType", "월세", "buildingType", "원룸",
                "status", "pending", "createdAt", "2026-10-06T09:30:00"),
            Map.of("id", 8002, "owner", "중개인***", "title", "채광 좋은 투룸 데모",
                "address", "경기도 성남시 ***", "dealType", "전세", "buildingType", "다세대",
                "status", "review", "createdAt", "2026-10-05T13:10:00")
        ));
    }

    @GetMapping("/visits")
    public Map<String, Object> visits(HttpSession session) {
        requireGuest(session);
        return Map.of("items", List.of(
            Map.of("id", 7001, "roomId", "DEMO-01", "title", "역세권 원룸 데모",
                "date", "2026-10-10", "time", "14:00:00", "phone", "010-****-1234",
                "question", "비식별 처리된 방문 문의", "status", "pending", "createdAt", "2026-10-06T11:00:00"),
            Map.of("id", 7002, "roomId", "DEMO-02", "title", "채광 좋은 투룸 데모",
                "date", "2026-10-11", "time", "16:30:00", "phone", "010-****-5678",
                "question", "포트폴리오 표시용 예약", "status", "approved", "createdAt", "2026-10-05T12:00:00")
        ));
    }

    @GetMapping("/community/posts")
    public Map<String, Object> posts(HttpSession session) {
        requireGuest(session);
        return Map.of("items", List.of(
            Map.of("id", 6001, "username", "작성자***", "category", "tip", "title", "이사 준비 팁 데모",
                "area", "서울", "views", 42, "createdAt", "2026-10-06T08:00:00"),
            Map.of("id", 6002, "username", "회원***", "category", "review", "title", "동네 생활 후기 데모",
                "area", "경기", "views", 31, "createdAt", "2026-10-05T18:15:00")
        ));
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(HttpSession session) {
        requireGuest(session);
        return Map.of("items", List.of(
            Map.of("id", 5001, "admin", "관리자***", "action", "PROPERTY_REVIEWED",
                "targetType", "property", "targetId", "DEMO-8000", "details", "포트폴리오용 가상 감사 기록",
                "createdAt", "2026-10-06T16:20:00"),
            Map.of("id", 5002, "admin", "운영자***", "action", "INQUIRY_ANSWERED",
                "targetType", "inquiry", "targetId", "DEMO-9000", "details", "민감정보가 없는 데모 기록",
                "createdAt", "2026-10-05T17:30:00")
        ));
    }

    private void requireGuest(HttpSession session) {
        auth.requireGuest(session);
    }
}
