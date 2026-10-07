package com.onrender.zipai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.onrender.zipai.service.ZipaiAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.ObjectMapper;

class GuestReadOnlyFilterTest {
    private final GuestReadOnlyFilter filter = new GuestReadOnlyFilter(new ObjectMapper());

    @Test
    void guestMutationIsRejectedBeforeController() throws Exception {
        MockHttpServletRequest request = guestRequest("POST", "/api/properties");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("게스트 모드에서는 조회만 가능합니다.");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void guestGetAndReadOnlyRecommendationPostAreAllowed() throws Exception {
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        MockFilterChain getChain = new MockFilterChain();
        filter.doFilter(guestRequest("GET", "/api/properties"), getResponse, getChain);

        MockHttpServletResponse recommendationResponse = new MockHttpServletResponse();
        MockFilterChain recommendationChain = new MockFilterChain();
        filter.doFilter(
            guestRequest("POST", "/api/lifestyle/recommend/ml"),
            recommendationResponse,
            recommendationChain
        );

        assertThat(getChain.getRequest()).isNotNull();
        assertThat(recommendationChain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest guestRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ZipaiAuthService.GUEST_SESSION, Boolean.TRUE);
        request.setSession(session);
        return request;
    }
}
