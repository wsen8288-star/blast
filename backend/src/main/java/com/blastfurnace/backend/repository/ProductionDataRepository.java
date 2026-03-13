package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ProductionData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;

@Repository
public interface ProductionDataRepository extends JpaRepository<ProductionData, Long> {
    List<ProductionData> findByFurnaceId(String furnaceId);
    List<ProductionData> findByFurnaceIdAndTimestampBetween(String furnaceId, Date startDate, Date endDate);
    List<ProductionData> findByFurnaceIdAndTimestampBetweenOrderByTimestampDesc(String furnaceId, Date startDate, Date endDate, Pageable pageable);
    List<ProductionData> findByTimestampBetween(Date startDate, Date endDate);
    List<ProductionData> findByTimestampBetweenOrderByTimestampDesc(Date startDate, Date endDate, Pageable pageable);
    List<ProductionData> findByFurnaceIdOrderByTimestampDesc(String furnaceId);
    List<ProductionData> findByFurnaceIdOrderByTimestampDesc(String furnaceId, Pageable pageable);
    List<ProductionData> findTop50ByFurnaceIdOrderByTimestampDesc(String furnaceId);
    List<ProductionData> findAllByOrderByTimestampDesc();
    List<ProductionData> findAllByOrderByTimestampDesc(Pageable pageable);
    long deleteByCollectionHistoryId(Long collectionHistoryId);
    long deleteByTimestampBetween(Date startDate, Date endDate);
    long deleteByTimestampBefore(Date cutoff);
}
