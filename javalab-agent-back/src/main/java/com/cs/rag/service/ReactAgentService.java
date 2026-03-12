package com.cs.rag.service;

import reactor.core.publisher.Flux;

public interface ReactAgentService {
    Flux<String> chat(String message, String sessionId, Long userId, String model);
}
