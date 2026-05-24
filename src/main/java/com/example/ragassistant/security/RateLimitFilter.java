package com.example.ragassistant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 120;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        synchronized (counter) {
            if (now - counter.windowStart > WINDOW_MILLIS) {
                counter.windowStart = now;
                counter.count.set(0);
            }
            if (counter.count.incrementAndGet() > REQUESTS_PER_MINUTE) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "error", "Too Many Requests",
                        "message", "Rate limit exceeded"
                )));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static class WindowCounter {
        private long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
