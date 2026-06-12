package com.example.facedetection.controller;

import com.example.facedetection.dto.profile.CreateProfileRequest;
import com.example.facedetection.dto.profile.ProfileResponse;
import com.example.facedetection.model.FaceEmbedding;
import com.example.facedetection.model.Profile;
import com.example.facedetection.service.*;
import com.example.facedetection.repositoty.FaceEmbeddingRepository;
import com.example.facedetection.repositoty.ProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/profiles")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;
    private final FaceRegistrationService faceRegistrationService;

    // ── Список профилей ───────────────────────────────────
    @GetMapping
    public String list(Model model) {
        List<ProfileResponse> profiles = profileService.getAll();
        model.addAttribute("profiles", profiles);
        return "profiles/list";
    }

    // ── Форма создания ────────────────────────────────────
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("profile", new CreateProfileRequest());
        model.addAttribute("editMode", false);
        return "profiles/form";
    }


    // ── Сохранение нового профиля ─────────────────────────
    @PostMapping
    public String create(@Valid @ModelAttribute("profile") CreateProfileRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", false);
            return "profiles/form";
        }
        ProfileResponse created = profileService.create(request);
        ra.addFlashAttribute("success", "Профіль створено");
        return "redirect:/profiles/" + created.id();
    }

    // ── Детальная страница профиля ────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProfileResponse profile = profileService.getById(id);
        model.addAttribute("profile", profile);
        return "profiles/detail";
    }
    @PostMapping("/{id}/face/augmented")
    public String registerFaceAugmented(@PathVariable Long id,
                                        @RequestParam("file") MultipartFile file,
                                        RedirectAttributes ra) {
        try {
            faceRegistrationService.registerFaceWithAugmentation(id, file.getBytes());
            ra.addFlashAttribute("success", "Зареєстровано 3 embedding'и (оригінал + дзеркало + яскравість)");
        } catch (Exception e) {
            log.error("Augmented face registration error: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profiles/" + id;
    }


    // ── Форма редактирования ──────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ProfileResponse p = profileService.getById(id);
        CreateProfileRequest req = new CreateProfileRequest();
        req.setFirstName(p.firstName());
        req.setLastName(p.lastName());
        req.setBiography(p.biography());
        req.setEmail(p.email());
        req.setPhoneNumber(p.phoneNumber());
        req.setAddress(p.address());
        req.setCity(p.city());
        req.setBirthDate(p.birthDate());
        req.setAvatarUrl(p.avatarUrl());
        model.addAttribute("profile", req);
        model.addAttribute("profileId", id);
        model.addAttribute("editMode", true);
        return "profiles/form";
    }

    // ── Обновление профиля ────────────────────────────────
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("profile") CreateProfileRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", true);
            model.addAttribute("profileId", id);
            return "profiles/form";
        }
        profileService.update(id, request);
        ra.addFlashAttribute("success", "Профіль оновлено");
        return "redirect:/profiles/" + id;
    }

    // ── Удаление профиля ──────────────────────────────────
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        profileService.delete(id);
        ra.addFlashAttribute("success", "Профіль видалено");
        return "redirect:/profiles";
    }

    // ── Загрузка аватара ──────────────────────────────────
    @PostMapping("/{id}/avatar")
    public String uploadAvatar(@PathVariable Long id,
                               @RequestParam("file") MultipartFile file,
                               RedirectAttributes ra) {
        try {
            profileService.uploadAvatar(id, file);
            ra.addFlashAttribute("success", "Фото завантажено");
        } catch (Exception e) {
            log.error("Avatar upload error: {}", e.getMessage());
            ra.addFlashAttribute("error", "Помилка завантаження фото");
        }
        return "redirect:/profiles/" + id;
    }

    @PostMapping("/{id}/face")
    public String registerFace(@PathVariable Long id,
                               @RequestParam("file") MultipartFile file,
                               RedirectAttributes ra) {
        try {
            faceRegistrationService.registerFace(id, file.getBytes());
            ra.addFlashAttribute("success", "Обличчя зареєстровано");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profiles/" + id;
    }


    // ── Регистрация из текущего аватара ──────────────────────
    @PostMapping("/{id}/face/from-avatar")
    public String registerFaceFromAvatar(@PathVariable Long id, RedirectAttributes ra) {
        try {
            faceRegistrationService.registerFaceFromAvatar(id);
            ra.addFlashAttribute("success", "Обличчя зареєстровано з аватара");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profiles/" + id;
    }
}