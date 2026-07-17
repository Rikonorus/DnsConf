package com.novibe.dns.next_dns.http;

import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.exception.ProcessException;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.dto.response.NextDnsResponse;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

@UtilityClass
public class NextDnsRateLimitedApiProcessor {

    static final Duration RATE_LIMIT_DEADLINE = Duration.ofMinutes(60);
    private static final Duration RATE_LIMIT_RESET_WAIT = Duration.ofMinutes(1);
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(1);

    public <D, R extends NextDnsResponse<?>> void callApi(List<D> requestList, Function<D, R> request) {
        callApi(requestList, request, RATE_LIMIT_DEADLINE, System::nanoTime,
                NextDnsRateLimitedApiProcessor::runResetWaitTimer);
    }

    static <D, R extends NextDnsResponse<?>> void callApi(
            List<D> requestList,
            Function<D, R> request,
            Duration rateLimitDeadline,
            NanoClock nanoClock,
            DurationSleeper sleeper
    ) {
        Queue<D> requestQueue = new ArrayDeque<>(requestList);
        long deadlineNanos = nanoClock.nanoTime() + rateLimitDeadline.toNanos();
        int successCounter = 0;
        int waveCounter = 0;
        while (!requestQueue.isEmpty()) {
            if (isDeadlineExpired(deadlineNanos, nanoClock)) {
                throw rateLimitDeadlineExceeded();
            }
            D requestDto = requestQueue.poll();
            try {
                R response = request.apply(requestDto);
                if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                    throw new ProcessException("NextDNS returned an API error");
                } else {
                    Log.progress("Current success progress: " + ++successCounter + "/" + requestList.size());
                    waveCounter++;
                }
            } catch (DnsHttpError e) {
                if (e.getCode() == 524 || e.getCode() == 429) {
                    requestQueue.add(requestDto);
                    Log.common("Sending speed: %s requests per second"
                            .formatted((double) waveCounter / 60));
                    Log.common("Code %s. Api rate limit has reached".formatted(e.getCode()));
                    waitForRateLimitReset(deadlineNanos, nanoClock, sleeper);
                    Log.io("Continue...");
                    waveCounter = 0;
                } else {
                    throw new ProcessException("NextDNS request failed with HTTP " + e.getCode(), e);
                }
            }
        }
        Log.common("\nCompleted");
    }

    private void waitForRateLimitReset(long deadlineNanos, NanoClock nanoClock, DurationSleeper sleeper) {
        long remainingNanos = deadlineNanos - nanoClock.nanoTime();
        if (remainingNanos <= 0) {
            throw rateLimitDeadlineExceeded();
        }
        Duration pause = Duration.ofNanos(Math.min(remainingNanos, RATE_LIMIT_RESET_WAIT.toNanos()));
        try {
            sleeper.sleep(pause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessException("NextDNS rate-limit wait interrupted");
        }
        if (pause.compareTo(RATE_LIMIT_RESET_WAIT) < 0 || isDeadlineExpired(deadlineNanos, nanoClock)) {
            throw rateLimitDeadlineExceeded();
        }
    }

    private boolean isDeadlineExpired(long deadlineNanos, NanoClock nanoClock) {
        return nanoClock.nanoTime() >= deadlineNanos;
    }

    private ProcessException rateLimitDeadlineExceeded() {
        return new ProcessException("NextDNS rate-limit deadline exceeded");
    }

    private void runResetWaitTimer(Duration pause) throws InterruptedException {
        Duration remaining = pause;
        while (!remaining.isZero() && !remaining.isNegative()) {
            Duration sleep = remaining.compareTo(PROGRESS_INTERVAL) > 0 ? PROGRESS_INTERVAL : remaining;
            Thread.sleep(sleep);
            remaining = remaining.minus(sleep);
            Log.progress("Waiting for reset: " + Math.max(0, remaining.toSeconds()) + " seconds");
        }
    }

    @FunctionalInterface
    interface NanoClock {
        long nanoTime();
    }

    @FunctionalInterface
    interface DurationSleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

}
