
CREATE EXTENSION IF NOT EXISTS vector;

-- Очистити старі embeddings перед перереєстрацією
DELETE FROM face_embedding;