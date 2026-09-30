package com.onrender.zipai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.HousingNotice;

public interface HousingNoticeRepository extends CrudRepository<HousingNotice, Long> {

    List<HousingNotice> findAllByOrderByNoticeDateDescNoticeIdDesc();

    Optional<HousingNotice> findByPanId(String panId);
}
