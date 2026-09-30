package com.onrender.zipai.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PropertyListingImageStorageService {
    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path root = Path.of("uploads", "properties").toAbsolutePath().normalize();

    public PropertyListingImageStorageService() {
        try { Files.createDirectories(root); }
        catch (IOException e) { throw new IllegalStateException("매물 사진 저장 폴더를 만들 수 없습니다.", e); }
    }

    public StoredImage store(MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = contentType.equals("image/png") ? ".png" : contentType.equals("image/webp") ? ".webp" : ".jpg";
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        String originalName = sanitize(file.getOriginalFilename());
        Path target = root.resolve(storedName).normalize();
        if (!target.getParent().equals(root)) throw new IllegalArgumentException("올바르지 않은 사진 경로입니다.");
        try { Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new IllegalStateException("매물 사진 저장에 실패했습니다.", e); }
        return new StoredImage(originalName, storedName, "/api/properties/images/" + storedName);
    }

    public Resource load(String storedName) {
        if (storedName == null || !storedName.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("올바르지 않은 이미지 경로입니다.");
        Path target = root.resolve(storedName).normalize();
        if (!target.getParent().equals(root)) throw new IllegalArgumentException("올바르지 않은 이미지 경로입니다.");
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new IllegalArgumentException("이미지를 찾을 수 없습니다.");
            return resource;
        } catch (IOException e) { throw new IllegalStateException("이미지를 읽을 수 없습니다.", e); }
    }

    public String contentType(String storedName) {
        try {
            String type = Files.probeContentType(root.resolve(storedName).normalize());
            return type == null ? "application/octet-stream" : type;
        } catch (IOException e) { return "application/octet-stream"; }
    }

    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        if (!storedName.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("올바르지 않은 이미지 경로입니다.");
        Path target = root.resolve(storedName).normalize();
        if (!target.getParent().equals(root)) throw new IllegalArgumentException("올바르지 않은 이미지 경로입니다.");
        try { Files.deleteIfExists(target); }
        catch (IOException e) { throw new IllegalStateException("매물 사진 삭제에 실패했습니다.", e); }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("빈 사진은 업로드할 수 없습니다.");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("사진 한 장은 5MB 이하만 업로드할 수 있습니다.");
        String type = file.getContentType();
        if (type == null || !ALLOWED.contains(type.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("사진은 JPG, PNG, WEBP 형식만 가능합니다.");
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "image";
        return Path.of(name).getFileName().toString().replaceAll("[\\r\\n]", "_");
    }

    public record StoredImage(String originalName, String storedName, String imageUrl) {}
}
