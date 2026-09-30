package com.onrender.zipai.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import com.onrender.zipai.domain.PropertyImage;

public interface PropertyImageRepository extends CrudRepository<PropertyImage, Long> {
    List<PropertyImage> findByPropertyIdOrderBySortOrderAscImageIdAsc(Long propertyId);
}
