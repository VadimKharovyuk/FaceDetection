package com.example.facedetection.service.impl;

import ai.onnxruntime.*;
import com.example.facedetection.service.FaceEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.util.Map;

import static org.bytedeco.opencv.global.opencv_core.merge;
import static org.bytedeco.opencv.global.opencv_core.split;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

@Service
@Slf4j
public class FaceEmbeddingServiceImpl implements FaceEmbeddingService {

    @Value("${app.models.recognition}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession     session;
    private String         inputName;
    private String         outputName;

    @PostConstruct
    public void init() throws OrtException, IOException {
        log.info("Loading ArcFace model: {}", modelPath);
        env = OrtEnvironment.getEnvironment();

        ClassPathResource resource = new ClassPathResource(
                modelPath.replace("classpath:", "")
        );
        byte[] modelBytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        session = env.createSession(modelBytes, new OrtSession.SessionOptions());

        inputName  = session.getInputNames().iterator().next();
        outputName = session.getOutputNames().iterator().next();

        log.info("✅ ArcFace loaded. Input: '{}', Output: '{}'", inputName, outputName);
    }

    @Override
    public float[] generateEmbedding(Mat faceMat) throws OrtException {

        // Крок 1: CLAHE — нормалізація освітлення (нічні фото, кольорове підсвічування)
        Mat normalized = applyClahe(faceMat);

        // Крок 2: resize до 112×112 — використовуємо normalized, не оригінал!
        Mat resized = new Mat();
        resize(normalized, resized, new Size(112, 112)); // ← fix: normalized замість faceMat

        // Крок 3: BGR → RGB
        Mat rgb = new Mat();
        cvtColor(resized, rgb, COLOR_BGR2RGB);

        // Крок 4: Mat → float[1][3][112][112] + нормалізація пікселів
        float[][][][] input = buildInputTensor(rgb);

        // Крок 5: ONNX inference
        OnnxTensor tensor = OnnxTensor.createTensor(env, input);
        try (OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            float[][] output = (float[][]) result.get(outputName).get().getValue();
            return l2Normalize(output[0]);
        }
    }

    @Override
    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    // ── private ───────────────────────────────────────────

    /**
     * CLAHE (Contrast Limited Adaptive Histogram Equalization).
     * Нормалізує яскравість: нічне фото ≈ студійне фото для ArcFace.
     * Працює тільки на каналі яскравості (L) у LAB просторі — колір не спотворюється.
     */
    private Mat applyClahe(Mat faceMat) {
        // BGR → LAB (L = яскравість, A/B = колір)
        Mat lab = new Mat();
        cvtColor(faceMat, lab, COLOR_BGR2Lab);

        // Розбиваємо на 3 канали
        MatVector channels = new MatVector(3);
        split(lab, channels);

        // CLAHE тільки на L-канал
        CLAHE clahe = createCLAHE(2.0, new Size(8, 8));
        Mat lChannel = new Mat();
        clahe.apply(channels.get(0), lChannel);
        channels.put(0, lChannel);

        // Збираємо назад і конвертуємо в BGR
        Mat result = new Mat();
        merge(channels, result);
        cvtColor(result, result, COLOR_Lab2BGR);
        return result;
    }

    /**
     * Будує тензор [1, 3, 112, 112].
     * & 0xFF: Java byte знаковий (-128..127), потрібне беззнакове (0..255).
     * Нормалізація: (pixel - 127.5) / 128.0 → діапазон [-1, 1].
     */
    private float[][][][] buildInputTensor(Mat rgb) {
        float[][][][] tensor = new float[1][3][112][112];
        int totalBytes = (int) (rgb.total() * rgb.channels());
        byte[] data = new byte[totalBytes];
        BytePointer pointer = rgb.data();
        pointer.get(data);

        for (int h = 0; h < 112; h++) {
            for (int w = 0; w < 112; w++) {
                int idx = (h * 112 + w) * 3;
                tensor[0][0][h][w] = ((data[idx]     & 0xFF) - 127.5f) / 128.0f; // R
                tensor[0][1][h][w] = ((data[idx + 1] & 0xFF) - 127.5f) / 128.0f; // G
                tensor[0][2][h][w] = ((data[idx + 2] & 0xFF) - 127.5f) / 128.0f; // B
            }
        }
        return tensor;
    }

    /**
     * L2 нормалізація. Після неї cosine similarity = dot product.
     * pgvector <=> оператор це cosine distance = 1 - dot product.
     */
    private float[] l2Normalize(float[] vector) {
        float norm = 0f;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm == 0f) return vector;

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }
}