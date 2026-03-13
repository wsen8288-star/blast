package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.CollectionHistory;
import com.blastfurnace.backend.repository.CollectionHistoryRepository;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CollectionHistoryService {
    @Autowired
    private CollectionHistoryRepository collectionHistoryRepository;

    @Autowired
    private ProductionDataRepository productionDataRepository;
    
    public List<CollectionHistory> findAll() {
        return collectionHistoryRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "startTime"));
    }
    
    public CollectionHistory findById(Long id) {
        return collectionHistoryRepository.findById(id).orElse(null);
    }
    
    public CollectionHistory save(CollectionHistory history) {
        return collectionHistoryRepository.save(history);
    }
    
    @Transactional
    public void delete(Long id) {
        CollectionHistory history = collectionHistoryRepository.findById(id).orElse(null);
        if (history != null) {
            long deleted = productionDataRepository.deleteByCollectionHistoryId(id);
            if (deleted == 0) {
                Date startTime = history.getStartTime();
                Date endTime = history.getEndTime() != null ? history.getEndTime() : new Date();
                if (startTime != null) {
                    productionDataRepository.deleteByTimestampBetween(startTime, endTime);
                }
            }
            // 删除对应的采集文件
            String filePath = history.getFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                File file = new File(filePath);
                System.out.println("[DELETE] 尝试删除物理文件: " + filePath);
                if (file.exists()) {
                    boolean fileDeleted = file.delete();
                    if (fileDeleted) {
                        System.out.println("[DELETE] 物理文件删除成功: " + filePath);
                    } else {
                        System.out.println("[DELETE] 物理文件删除失败 (可能被占用): " + filePath);
                    }
                } else {
                    System.out.println("[DELETE] 物理文件不存在，跳过删除: " + filePath);
                }
            }
            // 删除采集历史记录
            collectionHistoryRepository.deleteById(id);
        }
    }
    
    /**
     * 根据条件查询采集历史记录
     * @param taskName 任务名称
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 采集历史记录列表
     */
    public List<CollectionHistory> search(String taskName, String startDate, String endDate) {
        Specification<CollectionHistory> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 任务名称模糊查询
            if (taskName != null && !taskName.isEmpty()) {
                predicates.add(criteriaBuilder.like(root.get("taskName"), "%" + taskName + "%"));
            }
            
            // 开始日期查询
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date start = sdf.parse(startDate);
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("startTime"), start));
                } catch (ParseException e) {
                    // 日期解析失败，忽略该条件
                }
            }
            
            // 结束日期查询
            if (endDate != null && !endDate.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date end = sdf.parse(endDate);
                    // 设置为当天的最后一秒
                    end.setHours(23);
                    end.setMinutes(59);
                    end.setSeconds(59);
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startTime"), end));
                } catch (ParseException e) {
                    // 日期解析失败，忽略该条件
                }
            }
            
            // 组合条件
            Predicate[] predicateArray = new Predicate[predicates.size()];
            predicates.toArray(predicateArray);
            
            // 排序：按开始时间倒序
            query.orderBy(criteriaBuilder.desc(root.get("startTime")));
            
            return criteriaBuilder.and(predicateArray);
        };
        
        return collectionHistoryRepository.findAll(spec);
    }
    
    /**
     * 根据条件查询采集历史记录（兼容旧方法）
     * @param taskName 任务名称
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 采集历史记录列表
     */
    public List<CollectionHistory> findWithConditions(String taskName, String startDate, String endDate) {
        return search(taskName, startDate, endDate);
    }
    
    /**
     * 根据文件路径删除对应的采集历史记录
     * @param filePath 文件路径
     */
    @Transactional
    public void deleteByFilePath(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            // 查询所有采集历史记录
            List<CollectionHistory> allHistory = collectionHistoryRepository.findAll();
            
            for (CollectionHistory history : allHistory) {
                String historyFilePath = history.getFilePath();
                if (filePath.equals(historyFilePath)) {
                    delete(history.getId());
                    break;
                }
            }
        }
    }
}
