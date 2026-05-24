package com.example.ragassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragassistant.util.TextChunker;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void shouldCreateOverlappingChunks() {
        String text = IntStream.range(0, 1000)
                .mapToObj(i -> "w" + i)
                .collect(Collectors.joining(" "));

        TextChunker chunker = new TextChunker();
        var chunks = chunker.chunk(text, 400, 80);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).split(" ").length).isEqualTo(400);
        assertThat(chunks.get(1)).contains("w320");
    }
}
