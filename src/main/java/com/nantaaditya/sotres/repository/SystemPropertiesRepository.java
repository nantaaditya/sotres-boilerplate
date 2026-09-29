package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.SystemProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemPropertiesRepository extends ListCrudRepository<SystemProperties, Long> {
  List<SystemProperties> findByGroupId(String groupId);
  Optional<SystemProperties> findByGroupIdAndPropertyId(String groupId, String propertyId);
}
