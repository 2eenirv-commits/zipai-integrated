package com.onrender.zipai.repository;

import com.onrender.zipai.domain.FinancePolicy;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface FinancePolicyRepository extends CrudRepository<FinancePolicy, Long> {
    List<FinancePolicy> findAllByOrderByIdAsc();
    List<FinancePolicy> findByCategoryOrderByIdAsc(String category);
    List<FinancePolicy> findByTargetTypeOrderByIdAsc(String targetType);
    List<FinancePolicy> findByCategoryAndTargetTypeOrderByIdAsc(String category, String targetType);
}
