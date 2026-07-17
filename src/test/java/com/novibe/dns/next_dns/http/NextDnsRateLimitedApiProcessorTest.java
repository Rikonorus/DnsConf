package com.novibe.dns.next_dns.http;

import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.exception.ProcessException;
import com.novibe.dns.next_dns.http.dto.response.NextDnsResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NextDnsRateLimitedApiProcessorTest {

    @Test
    void completesAfterMany429And524WavesBeforeTheFiniteDeadline() {
        AtomicLong nanoseconds = new AtomicLong();
        Map<Integer, Integer> attempts = new HashMap<>();
        java.util.ArrayList<Integer> callOrder = new java.util.ArrayList<>();

        NextDnsRateLimitedApiProcessor.callApi(
                List.of(1, 2),
                request -> {
                    callOrder.add(request);
                    int attempt = attempts.merge(request, 1, Integer::sum);
                    if (attempt <= 3) {
                        throw httpError(attempt % 2 == 0 ? 524 : 429);
                    }
                    return new NextDnsResponse<>();
                },
                Duration.ofMinutes(10),
                nanoseconds::get,
                pause -> nanoseconds.addAndGet(pause.toNanos())
        );

        assertEquals(Map.of(1, 4, 2, 4), attempts);
        assertEquals(List.of(1, 2, 1, 2, 1, 2, 1, 2), callOrder);
        assertEquals(Duration.ofMinutes(6).toNanos(), nanoseconds.get());
    }

    @Test
    void throwsSafeFailureAtTheRateLimitDeadlineWithoutInfiniteRetry() {
        AtomicLong nanoseconds = new AtomicLong();
        AtomicInteger attempts = new AtomicInteger();

        ProcessException exception = assertThrows(ProcessException.class, () -> NextDnsRateLimitedApiProcessor.callApi(
                List.of("rewrite"),
                request -> {
                    attempts.incrementAndGet();
                    throw httpError(429);
                },
                Duration.ofMinutes(2),
                nanoseconds::get,
                pause -> nanoseconds.addAndGet(pause.toNanos())
        ));

        assertEquals("NextDNS rate-limit deadline exceeded", exception.getMessage());
        assertEquals(2, attempts.get());
    }

    private DnsHttpError httpError(int code) {
        HttpResponse<?> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        return new DnsHttpError(response, null);
    }
}
