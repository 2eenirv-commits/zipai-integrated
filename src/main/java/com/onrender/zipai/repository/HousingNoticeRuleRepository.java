package com.onrender.zipai.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.HousingNoticeRule;

public interface HousingNoticeRuleRepository extends CrudRepository<HousingNoticeRule, Long> {

    List<HousingNoticeRule> findByNoticeIdOrderByApplicantTypeAscSourcePdfAsc(Long noticeId);

    List<HousingNoticeRule> findByNoticeIdAndApplicantTypeOrderByUpdatedAtDesc(
            Long noticeId,
            String applicantType);

    boolean existsByNoticeIdAndApplicantType(Long noticeId, String applicantType);
}
