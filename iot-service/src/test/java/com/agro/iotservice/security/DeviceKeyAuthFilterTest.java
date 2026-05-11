package com.agro.iotservice.security;

import com.agro.iotservice.repository.DeviceApiKeyRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceKeyAuthFilterTest {

    @Mock DeviceApiKeyRepository repo;

    @InjectMocks DeviceKeyAuthFilter filter;

    @Test
    void otherPath_bypassesFilter() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/sensor");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void ingestPath_isFiltered() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/ingest/sensor/00000000-0000-0000-0000-000000000000/reading");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void missingHeader_returns401() throws Exception {
        UUID sid = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/ingest/sensor/" + sid + "/reading");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void wrongKey_returns401() throws Exception {
        UUID sid = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/ingest/sensor/" + sid + "/reading");
        req.addHeader("X-Device-Key", "bad");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(repo.verifyActiveKey(sid, "bad")).thenReturn(false);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void validKey_passesThrough() throws Exception {
        UUID sid = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/ingest/sensor/" + sid + "/reading");
        req.addHeader("X-Device-Key", "good");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(repo.verifyActiveKey(sid, "good")).thenReturn(true);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void malformedUuid_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/ingest/sensor/not-a-uuid/reading");
        req.addHeader("X-Device-Key", "good");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}
