package com.wensaas.core.impl.webhook.dispatch;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.wensaas.core.impl.webhook.WebhookProperties;

/**
 * 封装 Spring 6 {@link RestClient} 给 {@link WebhookSenderConsumer} 用。
 *
 * <p>connect-timeout + read-timeout 由 {@link WebhookProperties} 控制。
 * 4xx/5xx 状态码不抛错，作为业务结果返回，由 Consumer 决策是否重试。
 */
public class WebhookHttpClient {

    private final RestClient client;
    private final int bodyTruncateBytes;

    public WebhookHttpClient(WebhookProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getHttpConnectTimeout().toMillis());
        factory.setReadTimeout((int) props.getHttpReadTimeout().toMillis());
        this.client = RestClient.builder().requestFactory(factory).build();
        this.bodyTruncateBytes = props.getBodyTruncateBytes();
    }

    /**
     * POST {@code targetUrl}（JSON body + headers）。返回 status + 截断后的 response body。
     * 连接 / 读超时 / DNS 失败抛 {@link WebhookHttpException}，由调用方按"5xx 失败"处理。
     */
    public Response post(String targetUrl, String body, Map<String, String> headers) {
        try {
            return client.post().uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> headers.forEach(h::set))
                    .body(body)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        byte[] bytes = res.getBody().readAllBytes();
                        String responseBody = bytes.length == 0 ? "" :
                                new String(bytes, 0, Math.min(bytes.length, bodyTruncateBytes),
                                        StandardCharsets.UTF_8);
                        HttpHeaders respHeaders = res.getHeaders();
                        return new Response(status, responseBody, respHeaders);
                    });
        } catch (RuntimeException e) {
            throw new WebhookHttpException("Webhook POST 失败: " + targetUrl, e);
        }
    }

    public record Response(int status, String body, HttpHeaders headers) {
        public boolean is2xx() {
            return status >= 200 && status < 300;
        }
    }

    public static class WebhookHttpException extends RuntimeException {
        public WebhookHttpException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
