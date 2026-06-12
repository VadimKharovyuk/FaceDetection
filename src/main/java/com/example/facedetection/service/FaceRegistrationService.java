package com.example.facedetection.service;


import java.io.IOException;

public interface FaceRegistrationService {

    // Регистрация из загруженных байтов фото
    void registerFace(Long profileId, byte[] imageBytes) throws IOException;

    // Регистрация из уже сохранённого avatarUrl (без повторной загрузки)
    void registerFaceFromAvatar(Long profileId) throws IOException;

    void registerFaceWithAugmentation(Long profileId, byte[] imageBytes) throws IOException;
}