# 🧠 Face Detection System

Система розпізнавання облич на базі нейронних мереж. Завантажте фото — система визначить хто це за лічені секунди.

## Як це працює

```
Фото → SCRFD (det_10g.onnx) → ArcFace (w600k_r50.onnx) → pgvector <=> cosine similarity → Профіль
```

1. **Детекція** — SCRFD знаходить і вирізає обличчя на фото
2. **Embedding** — ArcFace перетворює обличчя у вектор 512 чисел
3. **Пошук** — pgvector знаходить найближчий вектор у базі даних
4. **Результат** — повертає профіль з відсотком схожості

## Технічний стек

| Компонент | Технологія |
|-----------|-----------|
| Backend | Spring Boot 4, Java 17 |
| Face Detection | SCRFD (`det_10g.onnx`) |
| Face Recognition | ArcFace R50 (`w600k_r50.onnx`) або R100 (`glintr100.onnx`) |
| ML Runtime | ONNX Runtime 1.19.0 |
| Image Processing | JavaCV 1.5.10 |
| Database | PostgreSQL + pgvector |
| ORM | Spring Data JPA / Hibernate |
| Templates | Thymeleaf |
| Photo Storage | Cloudinary |

## Налаштування

### 1. Вимоги

- Java 17+
- PostgreSQL 14+
- Maven 3.8+

### 2. База даних

Створи базу даних і підключи розширення pgvector:

```sql
CREATE DATABASE FaceDetection;
\c FaceDetection
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. ONNX моделі (не в git)

Завантаж моделі з [InsightFace Releases](https://github.com/deepinsight/insightface/releases).

Є два варіанти — обирай один:

#### Варіант A — `buffalo_l.zip` (~275 MB) — стандартний

```
buffalo_l.zip → розпакуй → скопіюй в src/main/resources/models/
├── det_10g.onnx        (~17 MB)  — детекція (SCRFD)
├── w600k_r50.onnx      (~166 MB) — розпізнавання ArcFace R50
└── genderage.onnx      (~1 MB)   — стать і вік (опціонально)
```

В `application.properties`:
```properties
app.models.recognition=classpath:models/w600k_r50.onnx
```

#### Варіант B — `antelopev2.zip` (~344 MB) — точніший ⭐

```
antelopev2.zip → розпакуй → скопіюй glintr100.onnx в src/main/resources/models/
└── glintr100.onnx      (~280 MB) — розпізнавання ArcFace R100
```

Детекцію (`det_10g.onnx`) береш з `buffalo_l.zip`.

В `application.properties`:
```properties
app.models.recognition=classpath:models/glintr100.onnx
```

| | buffalo_l (R50) | antelopev2 (R100) |
|--|-----------------|-------------------|
| Датасет | WebFace 600K | Glint360K (17M фото) |
| Точність LFW | 99.7% | 99.8% |
| Швидкість на M1 | ~80ms | ~150ms |
| Стійкість до кепок / нічного освітлення | середня | краща |

> ⚠️ При зміні моделі — видали старі embeddings з БД і перереєструй обличчя:
> ```sql
> DELETE FROM face_embedding;
> ```

> ⚠️ Файли `.onnx` додані в `.gitignore` через розмір. Кожен розробник завантажує їх окремо.

### 4. Cloudinary (зберігання фото)

Зареєструйся на [cloudinary.com](https://cloudinary.com) і отримай credentials у Dashboard.

### 5. Локальний конфіг

Створи файл `application-local.properties` в корені проекту (він в `.gitignore`):

```properties
# ── Database ──────────────────────────────────────────────
spring.datasource.url=jdbc:postgresql://localhost:5432/FaceDetection
spring.datasource.username=postgres
spring.datasource.password=your_password

# ── Cloudinary ────────────────────────────────────────────
cloudinary.cloud-name=your_cloud_name
cloudinary.api-key=your_api_key
cloudinary.api-secret=your_api_secret
```

Активуй локальний профіль в `application.properties`:

```properties
spring.profiles.active=local
```

### 6. Запуск

```bash
mvn spring-boot:run
```

Відкрий браузер: [http://localhost:1117](http://localhost:1117)

## Як користуватися

### Реєстрація профілю
1. Перейди на `/profiles/new`
2. Заповни ім'я, прізвище, біографію
3. Завантаж аватар (кнопка "📷 Аватар" на сторінці профілю)
4. Зареєструй обличчя — кнопка **"🧠 Зареєструвати обличчя"** або **"🖼️ Зареєструвати з аватара"**

### Розпізнавання
1. Перейди на `/identify`
2. Завантаж фото (drag & drop або через діалог)
3. Натисни **"🧠 Розпізнати"**
4. Система покаже профіль та відсоток схожості

## Структура проекту

```
src/main/
├── java/com/example/facedetection/
│   ├── config/
│   │   └── CloudinaryConfig.java       — налаштування Cloudinary
│   ├── controller/
│   │   ├── HomeController.java
│   │   ├── ProfileController.java      — CRUD профілів + реєстрація обличь
│   │   ├── FaceController.java         — розпізнавання
│   │   └── GlobalModelAdvice.java      — currentUri для navbar
│   ├── dto/
│   │   ├── CreateProfileRequest.java
│   │   ├── ProfileResponse.java
│   │   ├── IdentificationResult.java
│   │   └── FaceMatchProjection.java
│   ├── model/
│   │   ├── Profile.java
│   │   └── FaceEmbedding.java          — vector(512) через @ColumnTransformer
│   ├── repository/
│   │   ├── ProfileRepository.java
│   │   └── FaceEmbeddingRepository.java — pgvector <=> пошук
│   ├── service/
│   │   ├── FaceDetectionService.java   — SCRFD, det_10g.onnx
│   │   ├── FaceEmbeddingService.java   — ArcFace, w600k_r50.onnx
│   │   ├── FaceIdentificationService.java
│   │   ├── FaceRegistrationService.java
│   │   ├── ProfileService.java
│   │   └── StorageService.java         — Cloudinary
│   └── exception/
│       └── ProfileNotFoundException.java
└── resources/
    ├── models/                         — .onnx файли (не в git)
    ├── templates/
    │   ├── fragments/layout.html       — navbar, footer
    │   ├── profiles/                   — list, form, detail
    │   ├── face/                       — identify, result
    │   └── home.html
    └── application.properties
```

## Точність

| Умова | Очікувана схожість |
|-------|--------------------|
| Те саме фото | ~1.00 |
| Різні фото, хороше освітлення | 0.75 – 0.95 |
| Різний кут, освітлення | 0.55 – 0.75 |
| Різні люди | < 0.45 |

Поріг за замовчуванням: `app.face.similarity-threshold=0.5`  
Налаштовується в `application.properties`.

## Відомі обмеження

- Точність знижується при куті повороту голови > 45°
- Погане освітлення або низька роздільність фото дає similarity < 0.5
- Рекомендується реєструвати 2-3 фото на профіль для кращої точності

## Ліцензія

MIT