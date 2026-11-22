package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /ai/embed response for a single text. */
@Getter
@NoArgsConstructor
public class AiEmbedResponse {
    private List<Double> embedding;
}
