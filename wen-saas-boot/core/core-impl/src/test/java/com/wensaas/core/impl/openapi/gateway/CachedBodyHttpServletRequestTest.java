package com.wensaas.core.impl.openapi.gateway;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CachedBodyHttpServletRequestTest {

    @Test
    void bodyReadableMultipleTimes() throws Exception {
        HttpServletRequest base = mock(HttpServletRequest.class);
        when(base.getCharacterEncoding()).thenReturn("UTF-8");
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest req = new CachedBodyHttpServletRequest(base, body);

        ServletInputStream first = req.getInputStream();
        byte[] firstRead = first.readAllBytes();

        ServletInputStream second = req.getInputStream();
        byte[] secondRead = second.readAllBytes();

        assertThat(firstRead).isEqualTo(body);
        assertThat(secondRead).isEqualTo(body);
        assertThat(req.getCachedBody()).isEqualTo(body);
        assertThat(req.getCachedBodyAsString()).isEqualTo("{\"hello\":\"world\"}");
    }

    @Test
    void readerProducesUtf8() throws Exception {
        HttpServletRequest base = mock(HttpServletRequest.class);
        when(base.getCharacterEncoding()).thenReturn(null);
        byte[] body = "中文 OK".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest req = new CachedBodyHttpServletRequest(base, body);
        BufferedReader r = req.getReader();
        assertThat(r.readLine()).isEqualTo("中文 OK");
    }

    @Test
    void nullBodyHandledGracefully() throws Exception {
        HttpServletRequest base = mock(HttpServletRequest.class);
        when(base.getCharacterEncoding()).thenReturn("UTF-8");
        CachedBodyHttpServletRequest req = new CachedBodyHttpServletRequest(base, null);
        assertThat(req.getCachedBody()).isEmpty();
        assertThat(req.getInputStream().readAllBytes()).isEmpty();
    }
}
