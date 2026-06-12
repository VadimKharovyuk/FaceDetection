package com.example.facedetection.service.impl;
import ai.onnxruntime.OrtException;
import com.example.facedetection.exception.ProfileNotFoundException;
import com.example.facedetection.model.FaceEmbedding;
import com.example.facedetection.model.Profile;
import com.example.facedetection.repositoty.FaceEmbeddingRepository;
import com.example.facedetection.repositoty.ProfileRepository;
import com.example.facedetection.service.FaceDetectionService;
import com.example.facedetection.service.FaceEmbeddingService;
import com.example.facedetection.service.FaceRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.bytedeco.opencv.global.opencv_core.flip;


@Service
@RequiredArgsConstructor
@Slf4j
public class FaceRegistrationServiceImpl implements FaceRegistrationService {

    private final FaceDetectionService    faceDetectionService;
    private final FaceEmbeddingService    faceEmbeddingService;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final ProfileRepository       profileRepository;

    // ── 1 embedding з байтів ──────────────────────────────
    @Override
    @Transactional
    public void registerFace(Long profileId, byte[] imageBytes) throws IOException {
        log.info("Registering face for profile {}", profileId);
        Profile profile = findProfile(profileId);
        processAndSave(profile, imageBytes);
    }

    // ── 1 embedding з avatarUrl ───────────────────────────
    @Override
    @Transactional
    public void registerFaceFromAvatar(Long profileId) throws IOException {
        Profile profile = findProfile(profileId);

        if (profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) {
            throw new IllegalStateException("Профіль не має аватара для реєстрації");
        }

        log.info("Registering face from avatarUrl for profile {}", profileId);
        byte[] imageBytes = downloadImage(profile.getAvatarUrl());
        processAndSave(profile, imageBytes);
    }

    // ── 3 embedding'и з одного фото (аугментація) ────────
    @Override
    @Transactional
    public void registerFaceWithAugmentation(Long profileId, byte[] imageBytes) throws IOException {
        log.info("Registering face with augmentation for profile {}", profileId);
        Profile profile = findProfile(profileId);

        try {
            Optional<Mat> faceOpt = faceDetectionService.detectAndCropFace(imageBytes);
            if (faceOpt.isEmpty()) {
                throw new IllegalStateException("Обличчя не знайдено на фото");
            }

            List<Mat> augmented = generateAugmentations(faceOpt.get());

            for (Mat aug : augmented) {
                float[] embedding    = faceEmbeddingService.generateEmbedding(aug);
                String  vectorString = faceEmbeddingService.toVectorString(embedding);

                faceEmbeddingRepository.save(FaceEmbedding.builder()
                        .profile(profile)
                        .embedding(vectorString)
                        .build());
            }

            log.info("✅ Registered {} embeddings for profile {}", augmented.size(), profileId);

        } catch (OrtException e) {
            throw new IOException("ONNX error: " + e.getMessage(), e);
        }
    }

    // ── private ───────────────────────────────────────────

    private void processAndSave(Profile profile, byte[] imageBytes) throws IOException {
        try {
            Optional<Mat> faceOpt = faceDetectionService.detectAndCropFace(imageBytes);
            if (faceOpt.isEmpty()) {
                throw new IllegalStateException("Обличчя не знайдено на фото");
            }

            float[] embedding    = faceEmbeddingService.generateEmbedding(faceOpt.get());
            String  vectorString = faceEmbeddingService.toVectorString(embedding);

            faceEmbeddingRepository.save(FaceEmbedding.builder()
                    .profile(profile)
                    .embedding(vectorString)
                    .build());

            log.info("✅ Face registered for profile {}", profile.getId());

        } catch (OrtException e) {
            throw new IOException("Помилка ONNX: " + e.getMessage(), e);
        }
    }

    /**
     * 3 варіанти одного обличчя:
     * 1. Оригінал
     * 2. Дзеркало по горизонталі — симулює поворот голови
     * 3. Підвищена яскравість — симулює інше освітлення
     * CLAHE вже застосовується всередині generateEmbedding()
     */
    private List<Mat> generateAugmentations(Mat face) {
        List<Mat> result = new ArrayList<>();

        // 1. Оригінал
        result.add(face);

        // 2. Дзеркало
        Mat flipped = new Mat();
        flip(face, flipped, 1);
        result.add(flipped);

        // 3. Яскравіше (+10%)
        Mat brighter = new Mat();
        face.convertTo(brighter, -1, 1.15, 10);
        result.add(brighter);

        return result;
    }

    private byte[] downloadImage(String url) throws IOException {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP error: " + response.statusCode());
            }
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запит перервано", e);
        }
    }

    private Profile findProfile(Long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException(id));
    }
}