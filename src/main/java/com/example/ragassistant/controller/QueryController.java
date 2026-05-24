package com.example.ragassistant.controller;

import com.example.ragassistant.dto.QueryRequest;
import com.example.ragassistant.dto.QueryResponse;
import com.example.ragassistant.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    @PostMapping
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return queryService.answer(request);
    }
}
