package com.blastfurnace.backend.model;

import jakarta.persistence.*; // Spring Boot 3.x 使用 jakarta, 2.x 使用 javax
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "report_history")
public class ReportHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reportName;    // 前端展示的名称
    private String fileName;      // 磁盘上的文件名
    private String filePath;      // 完整路径
    private Long fileSize;        // 文件大小
    private String reportType;    // 报表类型
    private Date createTime;      // 生成时间
    private String creator;       // 操作员
    
    @Column(columnDefinition = "TEXT")
    private String queryParams;   // 记录当时生成的参数，方便回溯
}