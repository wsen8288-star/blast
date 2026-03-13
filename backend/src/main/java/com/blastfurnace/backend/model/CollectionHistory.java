package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "collection_history")
public class CollectionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String taskName;
    
    @Column(nullable = false)
    private Date startTime;
    
    private Date endTime;
    
    @Column(nullable = false)
    private String status;
    
    private int recordCount;
    
    private String description;
    
    private String filePath;

    @Column(name = "run_id")
    private String runId;
}
