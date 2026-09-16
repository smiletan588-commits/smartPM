package com.smartpm.vo;

import lombok.Data;

@Data
public class AiOverviewVO {
    private long totalCalls;
    private double successRate;
    private long averageDurationMs;
    private double adoptionRate;
    private double averageRating;
    private double aiGeneratedTaskRate;
    private long durationSampleCount;
    private long ratingSampleCount;
    private long adoptionSampleCount;
}
