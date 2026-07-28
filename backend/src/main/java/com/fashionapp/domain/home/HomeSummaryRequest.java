package com.fashionapp.domain.home;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HomeSummaryRequest {
    private double temperature;
    private String condition;
    private String situation;
    private int budget;
}
