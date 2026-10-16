package com.byawasaya.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenServiceTest {
    @Test
    void permitsMultipleSessionsAndRecordsRequestDeviceDetails() {
        UserSessionRepository repo = mock(UserSessionRepository.class);
        TokenService service = new TokenService(repo);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("Firefox on Linux");
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");

        service.issue("account:user@example.com", "user@example.com", "STORE_OWNER", null, request);
        service.issue("account:user@example.com", "user@example.com", "STORE_OWNER", null, request);

        ArgumentCaptor<UserSession> sessions = ArgumentCaptor.forClass(UserSession.class);
        verify(repo, times(2)).saveAndFlush(sessions.capture());
        assertEquals("Firefox on Linux", sessions.getAllValues().get(0).getUserAgent());
        assertEquals("192.0.2.10", sessions.getAllValues().get(0).getIpAddress());
        assertEquals("account:user@example.com", sessions.getAllValues().get(1).getAccountKey());
    }
}
