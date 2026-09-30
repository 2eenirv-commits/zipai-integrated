package com.onrender.zipai.repository;

import com.onrender.zipai.domain.CommunityPost;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface CommunityPostRepository extends CrudRepository<CommunityPost, Long> {
    List<CommunityPost> findAllByOrderByCreatedAtDesc();
}
