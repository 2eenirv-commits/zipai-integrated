package com.onrender.zipai.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.onrender.zipai.dto.chat.ChatResponse;

@Service
public class ChatService {

    public ChatResponse reply(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();

        if (message.isBlank()) {
            return new ChatResponse(
                    "질문을 입력해 주세요. 행복주택, 생활지역 추천, 매물 찾기를 안내할 수 있어요.",
                    null,
                    null);
        }

        String normalized = message.toLowerCase(Locale.ROOT).replace(" ", "");

        if (containsAny(normalized, "안녕", "hello", "hi", "도움", "뭘할수", "무엇을할수")) {
            return new ChatResponse(
                    "안녕하세요. 현재 1차 챗봇은 행복주택 자격진단, Lifestyle 지역추천, 매물 찾기 기능을 안내합니다.",
                    null,
                    null);
        }

        if (containsAny(normalized, "행복주택", "공공임대", "자격진단", "입주자격")) {
            return new ChatResponse(
                    "행복주택에서는 나이, 소득, 자산, 자동차, 무주택 여부 등을 기준으로 자격진단을 확인할 수 있습니다.",
                    "행복주택 자격진단",
                    "/board/happy-housing");
        }

        if (containsAny(normalized, "lifestyle", "라이프스타일", "생활지역", "지역추천", "추천지역", "방구하기", "생활지표")) {
            return new ChatResponse(
                    "Lifestyle 분석에서는 사용자가 중요하게 생각하는 생활요소를 기준으로 경기도 지역을 비교하고 추천 결과를 확인할 수 있습니다.",
                    "Lifestyle 분석",
                    "/ai/lifestyle-analysis.html");
        }

        if (containsAny(normalized, "매물", "집찾기", "방찾기", "월세", "전세")) {
            return new ChatResponse(
                    "메인 화면에서 경기도 전·월세 매물을 검색하고 조건별로 확인할 수 있습니다.",
                    "매물 찾기",
                    "/");
        }

        if (containsAny(normalized, "계약", "사기", "안전", "체크리스트")) {
            return new ChatResponse(
                    "계약 안전 기능은 다른 기능과 통합 중입니다. 현재 1차 챗봇에서는 행복주택과 Lifestyle, 매물 찾기 안내를 우선 제공합니다.",
                    null,
                    null);
        }

        return new ChatResponse(
                "아직 1차 챗봇이라 해당 질문은 바로 답하기 어렵습니다. '행복주택', '생활지역 추천', '매물 찾기'처럼 질문해 주세요.",
                null,
                null);
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
