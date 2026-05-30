package com.example.ragassistant.repository;

import java.util.UUID;

public interface ChunkScoreProjection {

    UUID getChunkId();

    Double getScore();
}
