package com.example.facedetection.exception;
public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(Long id) {
        super("Профіль не знайдено: " + id);
    }
}
