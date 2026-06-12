package com.example.facedetection.dto.embedding;
import com.example.facedetection.dto.profile.ProfileResponse;

public record IdentificationResult(
        ProfileResponse profile,    // найденный профиль
        double similarity,          // 0.0 — 1.0  (1.0 = точное совпадение)
        boolean identified          // similarity > threshold
) {}