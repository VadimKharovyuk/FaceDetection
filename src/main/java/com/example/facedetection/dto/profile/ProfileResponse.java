package com.example.facedetection.dto.profile;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,        // firstName + " " + lastName
        String biography,
        String email,
        String phoneNumber,
        String address,
        String city,
        LocalDate birthDate,
        String avatarUrl,
        boolean hasEmbedding,   // зарегистрировано ли лицо
        LocalDateTime createdAt
) {}

