package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /ai/embed response for a batch ({"texts": [...]}), one vector per text in request order. */
@Getter
@NoArgsConstructor
public class AiEmbedBatchResponse {
    private List<List<Double>> embeddings;
}
