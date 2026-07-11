package com.wensaas.core.impl.openapi.gateway;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 让请求 body 可重复读：{@link AppKeyAuthenticationFilter} 读一次算 HMAC 签名，
 * Controller 再读一次反序列化业务参数。
 *
 * <p>Filter 在构造时一次性读完底层 InputStream，缓存到 {@link #body} 字节数组；
 * 之后 {@link #getInputStream()} / {@link #getReader()} 每次都基于该字节数组重建流。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body == null ? new byte[0] : body;
    }

    public byte[] getCachedBody() {
        return body;
    }

    public String getCachedBodyAsString() {
        Charset cs = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new String(body, cs);
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedInputStream(body);
    }

    @Override
    public BufferedReader getReader() {
        Charset cs = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), cs));
    }

    private static final class CachedInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        CachedInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override public int read() { return delegate.read(); }
        @Override public int read(byte[] b, int off, int len) { return delegate.read(b, off, len); }
        @Override public int available() { return delegate.available(); }
        @Override public boolean isFinished() { return delegate.available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }

    /** 工具：读完 {@code request.getInputStream()}，最大 maxBytes，超过返回 null。 */
    public static byte[] readBody(HttpServletRequest request, int maxBytes) throws IOException {
        try (var in = request.getInputStream();
             var out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
