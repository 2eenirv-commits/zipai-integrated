package com.onrender.zipai.web;

import com.onrender.zipai.domain.CommunityComment;
import com.onrender.zipai.domain.CommunityPost;
import com.onrender.zipai.domain.CommunityPostReport;
import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.repository.CommunityCommentRepository;
import com.onrender.zipai.repository.CommunityPostReportRepository;
import com.onrender.zipai.repository.CommunityPostRepository;
import com.onrender.zipai.repository.ZipaiUserRepository;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private static final int BLIND_REPORT_COUNT = 5;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final CommunityPostRepository posts;
    private final CommunityCommentRepository comments;
    private final CommunityPostReportRepository reports;
    private final ZipaiUserRepository users;
    private final ZipaiAuthService auth;
    private final JdbcTemplate jdbc;

    public CommunityController(
        CommunityPostRepository posts,
        CommunityCommentRepository comments,
        CommunityPostReportRepository reports,
        ZipaiUserRepository users,
        ZipaiAuthService auth,
        JdbcTemplate jdbc
    ) {
        this.posts = posts;
        this.comments = comments;
        this.reports = reports;
        this.users = users;
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @GetMapping("/posts")
    public Map<String, Object> list(HttpSession session) {
        ZipaiUser user = viewer(session);
        List<Map<String, Object>> items = posts.findAllByOrderByCreatedAtDesc().stream()
            .filter(post -> !isBlinded(post))
            .map(post -> postPayload(post, user == null ? null : user.getId()))
            .toList();
        return Map.of("items", items);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        String category = text(body, "category");
        String title = text(body, "title");
        String content = text(body, "content");
        String area = text(body, "area");
        String imageUrl = text(body, "imageUrl");
        int rating = "review".equals(category) ? number(body.get("rating")) : 0;

        if (!Set.of("review", "tip", "question", "free").contains(category) || title.length() < 2 || title.length() > 60 ||
            content.length() < 2 || content.length() > 2000 || area.length() > 30 || rating < 0 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시글 입력 내용을 확인해 주세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        CommunityPost post = new CommunityPost();
        post.setAuthorId(user.getId());
        post.setCategory(category);
        post.setTitle(title);
        post.setContent(content);
        post.setArea(area);
        post.setRating(rating);
        post.setViews(0L);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        post = posts.save(post);

        if (!imageUrl.isBlank()) {
            jdbc.update("INSERT INTO community_post_images(post_id,image_url,created_at) VALUES(?,?,?)", post.getId(), imageUrl, now);
        }
        return Map.of("item", postPayload(post, user.getId()));
    }

    @GetMapping("/posts/{id}")
    @Transactional
    public Map<String, Object> detail(@PathVariable Long id, HttpSession session) {
        ZipaiUser user = viewer(session);
        CommunityPost post = posts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));

        if (isBlinded(post) && (user == null
            || (!post.getAuthorId().equals(user.getId()) && !"admin".equals(user.getRole())))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "신고 누적으로 블라인드 처리된 게시글입니다.");
        }

        if (user != null) {
            post.setViews(post.getViews() + 1);
            post.setUpdatedAt(LocalDateTime.now());
            post = posts.save(post);
        }
        return Map.of("item", postPayload(post, user == null ? null : user.getId()));
    }

    @PutMapping("/posts/{id}/like")
    @Transactional
    public Map<String, Object> like(@PathVariable Long id, HttpSession session) {
        ZipaiUser user = auth.required(session);
        if (!posts.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        int added = jdbc.update(
            "INSERT IGNORE INTO community_post_likes(post_id,user_id,created_at) VALUES(?,?,?)",
            id, user.getId(), LocalDateTime.now()
        );
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM community_post_likes WHERE post_id=?", Long.class, id);
        return Map.of("liked", true, "added", added > 0, "likes", count == null ? 0L : count);
    }

    @GetMapping("/posts/{id}/comments")
    public Map<String, Object> comments(@PathVariable Long id, HttpSession session) {
        viewer(session);
        List<Map<String, Object>> items = comments.findByPostIdOrderByCreatedAtAsc(id).stream()
            .map(this::commentPayload)
            .toList();
        return Map.of("items", items);
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> comment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        if (!posts.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        String content = text(body, "content");
        if (content.isBlank() || content.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글을 1~500자로 입력해 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        CommunityComment comment = new CommunityComment();
        comment.setPostId(id);
        comment.setAuthorId(user.getId());
        comment.setContent(content);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        comment = comments.save(comment);
        return Map.of("item", commentPayload(comment));
    }

    @PostMapping("/posts/{id}/report")
    @Transactional
    public Map<String, Object> reportPost(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        CommunityPost post = posts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));

        if (post.getAuthorId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인이 작성한 게시글은 신고할 수 없습니다.");
        }
        if (reports.existsByPostIdAndUserId(id, user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 신고한 게시글입니다.");
        }

        String reason = text(body, "reason");
        if (reason.isBlank()) reason = "부적절한 내용";
        if (reason.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유는 255자 이하로 입력해 주세요.");
        }

        CommunityPostReport report = new CommunityPostReport();
        report.setPostId(id);
        report.setUserId(user.getId());
        report.setReason(reason);
        report.setCreatedAt(LocalDateTime.now());
        reports.save(report);

        long count = reports.countByPostId(id);
        return Map.of("reported", true, "reportsCount", count, "isBlinded", count >= BLIND_REPORT_COUNT);
    }

    @PostMapping("/posts/upload")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file, HttpSession session) {
        auth.required(session);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다.");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지는 10MB 이하만 업로드할 수 있습니다.");
        }

        String extension = extension(file.getOriginalFilename());
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPG, JPEG, PNG 이미지만 업로드할 수 있습니다.");
        }

        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다.");
            }

            File dir = new File("static/uploads/community");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("업로드 폴더를 만들 수 없습니다.");
            }

            String outputFormat = "jpeg".equals(extension) ? "jpg" : extension;
            String fileName = UUID.randomUUID() + "." + outputFormat;
            File destination = new File(dir, fileName);
            BufferedImage output = resize(original, 800, outputFormat);
            if (!ImageIO.write(output, outputFormat, destination)) {
                throw new IOException("지원하지 않는 이미지 형식입니다.");
            }
            return Map.of("url", "/static/uploads/community/" + fileName);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다.");
        }
    }

    private Map<String, Object> postPayload(CommunityPost post, Long userId) {
        long likes = count("SELECT COUNT(*) FROM community_post_likes WHERE post_id=?", post.getId());
        long likedCount = userId == null ? 0L
            : count("SELECT COUNT(*) FROM community_post_likes WHERE post_id=? AND user_id=?", post.getId(), userId);
        long reportCount = reports.countByPostId(post.getId());
        String author = users.findById(post.getAuthorId()).map(ZipaiUser::getUsername).orElse("알 수 없음");
        String imageUrl = jdbc.query(
            "SELECT image_url FROM community_post_images WHERE post_id=? ORDER BY id DESC LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : "",
            post.getId()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", post.getId());
        result.put("category", post.getCategory());
        result.put("title", post.getTitle());
        result.put("content", post.getContent());
        result.put("author", author);
        result.put("authorId", post.getAuthorId());
        result.put("area", post.getArea());
        result.put("rating", post.getRating());
        result.put("likes", likes);
        result.put("views", post.getViews());
        result.put("commentCount", comments.countByPostId(post.getId()));
        result.put("liked", likedCount > 0);
        result.put("imageUrl", imageUrl == null ? "" : imageUrl);
        result.put("reportsCount", reportCount);
        result.put("isBlinded", reportCount >= BLIND_REPORT_COUNT);
        result.put("createdAt", post.getCreatedAt());
        result.put("updatedAt", post.getUpdatedAt());
        return result;
    }

    private Map<String, Object> commentPayload(CommunityComment comment) {
        String author = users.findById(comment.getAuthorId()).map(ZipaiUser::getUsername).orElse("알 수 없음");
        return Map.of(
            "id", comment.getId(),
            "content", comment.getContent(),
            "author", author,
            "createdAt", comment.getCreatedAt()
        );
    }

    private boolean isBlinded(CommunityPost post) {
        return reports.countByPostId(post.getId()) >= BLIND_REPORT_COUNT;
    }

    private ZipaiUser viewer(HttpSession session) {
        if (auth.isGuest(session)) return null;
        return auth.required(session);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private static BufferedImage resize(BufferedImage original, int maxWidth, String format) {
        int width = Math.min(maxWidth, original.getWidth());
        int height = (int) Math.round(original.getHeight() * (width / (double) original.getWidth()));
        int imageType = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(original, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private static String text(Map<String, Object> body, String key) {
        return String.valueOf(body.getOrDefault(key, "")).trim();
    }

    private static int number(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception error) {
            return -1;
        }
    }
}
