package com.onrender.zipai.repository;

import com.onrender.zipai.domain.CommunityComment;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface CommunityCommentRepository extends CrudRepository<CommunityComment, Long> {
    List<CommunityComment> findByPostIdOrderByCreatedAtAsc(Long postId);
    long countByPostId(Long postId);
}
