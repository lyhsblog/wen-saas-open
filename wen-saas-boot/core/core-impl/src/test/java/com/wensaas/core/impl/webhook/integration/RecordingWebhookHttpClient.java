package com.wensaas.core.impl.webhook.integration;

import com.wensaas.core.impl.webhook.dispatch.WebhookHttpClient;
import com.wensaas.core.impl.webhook.WebhookProperties;
import org.springframework.http.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 测试用 {@link WebhookHttpClient}：按 URL 路由到预设响应（status + body），
 * 同时记录所有 POST 调用（method/url/body/headers）供断言。
 *
 * <p>支持顺序响应：每个 URL 可注册一个 {@code Function<Integer, Response>}，参数 = 该 URL 第几次被调用（1-based），
 * 让"前两次 502 / 第三次 200"这类重试场景可测。
 *
 * <p>未注册的 URL 默认返回 {@code 200 ""}。
 */
public class RecordingWebhookHttpClient extends WebhookHttpClient {

    private final Map<String, Function<Integer, Response>> handlers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Call> calls = new CopyOnWriteArrayList<>();

    public RecordingWebhookHttpClient(WebhookProperties props) {
        super(props);
    }

    @Override
    public Response post(String targetUrl, String body, Map<String, String> headers) {
        AtomicInteger counter = counters.computeIfAbsent(targetUrl, k -> new AtomicInteger(0));
        int n = counter.incrementAndGet();
        calls.add(new Call(targetUrl, body, new LinkedHashMap<>(headers)));
        Function<Integer, Response> h = handlers.get(targetUrl);
        if (h == null) {
            return new Response(200, "", new HttpHeaders());
        }
        return h.apply(n);
    }

    /** 注册：每次调用都返回相同的 status + body。 */
    public void onUrl(String url, int status, String responseBody) {
        handlers.put(url, n -> new Response(status, responseBody, new HttpHeaders()));
    }

    /** 注册：按调用次数路由响应（1-based）。 */
    public void onUrlSequence(String url, Function<Integer, Response> sequence) {
        handlers.put(url, sequence);
    }

    /** 注册：每次抛 HTTP 异常（模拟超时 / 连接错误）。 */
    public void onUrlThrows(String url, RuntimeException cause) {
        handlers.put(url, n -> {
            throw new WebhookHttpException("simulated", cause);
        });
    }

    public java.util.List<Call> getCalls() {
        return java.util.List.copyOf(calls);
    }

    public int callCount(String url) {
        AtomicInteger c = counters.get(url);
        return c == null ? 0 : c.get();
    }

    public void clear() {
        handlers.clear();
        counters.clear();
        calls.clear();
    }

    public record Call(String url, String body, Map<String, String> headers) {
    }
}
