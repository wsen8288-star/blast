package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.PreprocessingRequest;
import com.blastfurnace.backend.dto.PreprocessingResponse;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.DataProcessingService;
import com.blastfurnace.backend.service.ExternalImportTemplateService;
import com.blastfurnace.backend.service.IndustrialDataContract;
import com.blastfurnace.backend.service.SimulationQualityService;
import com.blastfurnace.backend.service.PreprocessHistoryService;
import com.blastfurnace.backend.service.UploadedDataNormalizer;
import com.blastfurnace.backend.service.UploadedFileService;
import com.blastfurnace.backend.service.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private static final Logger log = LoggerFactory.getLogger(DataController.class);
    private static final int PREVIEW_LIMIT = 200;
    private static final long SNAPSHOT_UPLOAD_LIMIT_BYTES = 5L * 1024L * 1024L;
    private static final int DATA_LIST_MAX_LIMIT = 5000;

    @Autowired
    private DataProcessingService dataProcessingService;

    @Autowired
    private ProductionDataRepository productionDataRepository;
    
    @Autowired
    private UploadedFileService uploadedFileService;

    @Autowired
    private SimulationQualityService simulationQualityService;
    
    @Autowired
    private PreprocessHistoryService preprocessHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExternalImportTemplateService externalImportTemplateService;

    @Autowired
    private SysConfigService sysConfigService;

    private int uploadRowLimit() {
        int configured = sysConfigService.getInt("data_upload_row_limit", 50000);
        return Math.max(configured, PREVIEW_LIMIT);
    }

    @GetMapping("/{id}")
    public Result<ProductionData> getDataDetail(@PathVariable Long id) {
        try {
            return productionDataRepository.findById(id)
                    .map(data -> Result.success(data, "获取数据详情成功"))
                    .orElse(Result.error("数据不存在"));
        } catch (Exception e) {
            log.error("获取数据详情失败", e);
            return Result.error("获取数据详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/submit")
    public Result<String> submitData(@RequestBody ProductionData data) {
        try {
            if (data.getFurnaceId() == null) {
                return Result.error("高炉ID不能为空");
            }
            if (data.getTimestamp() == null) {
                data.setTimestamp(new Date());
            }
            productionDataRepository.save(data);
            return Result.successMsg("数据提交成功");
        } catch (Exception e) {
            log.error("数据提交失败", e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null) return Result.error("文件名为空");
            List<Map<String, String>> resultList = parseTabularFile(file);
            return Result.success(buildUploadResponse(fileId(), fileName, file.getSize(), file.getContentType(), resultList), "文件解析成功");
        } catch (Exception e) {
            log.error("文件处理失败", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/import/templates")
    public Result<List<Map<String, Object>>> getImportTemplates() {
        return Result.success(externalImportTemplateService.listTemplates(), "获取外部导入模板成功");
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> previewExternalImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "templateKey", required = false) String templateKey
    ) {
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return Result.error("文件名为空");
            }
            List<Map<String, String>> rows = parseTabularFile(file);
            if (rows.isEmpty()) {
                return Result.error("文件无有效数据");
            }
            ExternalImportTemplateService.PreviewResult preview = externalImportTemplateService.preview(rows, templateKey);
            String previewId = UUID.randomUUID().toString();
            uploadedFileService.saveImportPreview(
                    new UploadedFileService.ImportPreviewSession(
                            previewId,
                            preview.templateKey(),
                            preview.normalizedRows(),
                            preview.originalHeaders(),
                            preview.headerMapping()
                    )
            );
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("previewId", previewId);
            resp.put("templateKey", preview.templateKey());
            resp.put("recordCount", preview.normalizedRows().size());
            resp.put("matchedFieldCount", new LinkedHashSet<>(preview.headerMapping().values()).size());
            resp.put("unmatchedHeaders", preview.unmatchedHeaders());
            resp.put("missingRequiredFields", preview.missingRequiredFields());
            resp.put("convertedCellCount", preview.convertedCellCount());
            resp.put("headerMapping", preview.headerMapping());
            resp.put("previewRows", preview.normalizedRows().stream().limit(PREVIEW_LIMIT).collect(Collectors.toList()));
            return Result.success(resp, "导入预览生成成功");
        } catch (Exception e) {
            log.error("导入预览失败", e);
            return Result.error("导入预览失败: " + e.getMessage());
        }
    }

    @PostMapping("/import/confirm")
    public Result<Map<String, Object>> confirmExternalImport(@RequestBody Map<String, Object> request) {
        try {
            String previewId = String.valueOf(request.getOrDefault("previewId", "")).trim();
            if (previewId.isEmpty()) {
                return Result.error("previewId不能为空");
            }
            UploadedFileService.ImportPreviewSession session = uploadedFileService.getImportPreview(previewId);
            if (session == null || session.rows() == null || session.rows().isEmpty()) {
                return Result.error("导入预览已过期或不存在，请重新上传");
            }
            List<String> required = List.of("temperature", "pressure", "windVolume", "coalInjection", "productionRate");
            Set<String> present = new LinkedHashSet<>();
            for (Map<String, String> row : session.rows()) {
                present.addAll(row.keySet());
                if (present.containsAll(required)) {
                    break;
                }
            }
            List<String> missing = required.stream().filter(key -> !present.contains(key)).collect(Collectors.toList());
            if (!missing.isEmpty()) {
                return Result.error("缺失关键字段: " + String.join(", ", missing));
            }
            String sourceName = String.valueOf(request.getOrDefault("fileName", "external_import.csv"));
            Map<String, Object> response = buildUploadResponse(fileId(), sourceName, 0L, "text/csv", session.rows());
            uploadedFileService.removeImportPreview(previewId);
            return Result.success(response, "导入确认成功");
        } catch (Exception e) {
            log.error("导入确认失败", e);
            return Result.error("导入确认失败: " + e.getMessage());
        }
    }

    private String fileId() {
        return UUID.randomUUID().toString();
    }

    private List<Map<String, String>> parseTabularFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名为空");
        }
        log.info("开始处理文件: {}, 大小: {}, 类型: {}", fileName, file.getSize(), file.getContentType());
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".csv")) {
            return parseCsvFile(file.getInputStream());
        }
        if (lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")) {
            return parseExcelFile(file.getInputStream());
        }
        throw new IllegalArgumentException("不支持的文件格式，仅支持 CSV, XLS, XLSX");
    }

    private Map<String, Object> buildUploadResponse(
            String fileId,
            String fileName,
            long fileSize,
            String fileType,
            List<Map<String, String>> resultList
    ) {
        List<Map<String, String>> fullRows = resultList == null ? List.of() : resultList;
        List<Map<String, String>> previewRows = fullRows.size() > PREVIEW_LIMIT ? fullRows.subList(0, PREVIEW_LIMIT) : fullRows;
        uploadedFileService.saveUploadedData(fileId, fullRows);
        UploadedDataNormalizer.NormalizedUpload normalizedUpload = UploadedDataNormalizer.normalize(fullRows);
        uploadedFileService.saveNormalizedData(
                fileId,
                new UploadedFileService.UploadedFileNormalized(
                        normalizedUpload.rows(),
                        normalizedUpload.originalHeaders(),
                        normalizedUpload.normalizedHeaders(),
                        normalizedUpload.headerMapping()
                )
        );
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("fileId", fileId);
        fileInfo.put("fileName", fileName);
        fileInfo.put("fileSize", fileSize);
        fileInfo.put("fileType", fileType);
        fileInfo.put("recordCount", fullRows.size());
        fileInfo.put("previewLimit", PREVIEW_LIMIT);
        fileInfo.put("headers", normalizedUpload.originalHeaders());
        fileInfo.put("normalizedHeaders", normalizedUpload.normalizedHeaders());
        fileInfo.put("headerMapping", normalizedUpload.headerMapping());
        Map<String, Object> response = new HashMap<>();
        response.put("data", previewRows);
        response.put("id", fileId);
        response.put("fileInfo", fileInfo);
        return response;
    }

    @PostMapping("/seed")
    public Result<String> seedData() {
        try {
            if (productionDataRepository.count() > 0) {
                return Result.successMsg("数据已存在，跳过初始化");
            }
            ProductionData data = new ProductionData();
            data.setFurnaceId("1");
            data.setTimestamp(new Date());
            data.setTemperature(1250.5);
            data.setPressure(0.25);
            data.setGasFlow(1800.0);
            data.setOxygenLevel(3.5);
            data.setMaterialHeight(1.8);
            data.setProductionRate(1450.0);
            data.setEnergyConsumption(520.0);
            data.setStatus("NORMAL");
            data.setOperator("SYSTEM");
            productionDataRepository.save(data);
            return Result.successMsg("初始化基准数据成功");
        } catch (Exception e) {
            return Result.error("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 使用 Apache POI 解析 Excel 文件
     */
    private List<Map<String, String>> parseExcelFile(InputStream inputStream) throws Exception {
        List<Map<String, String>> list = new ArrayList<>();
        int rowLimit = uploadRowLimit();
        
        // WorkbookFactory 自动支持 .xls 和 .xlsx
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // 默认读取第一个 Sheet
            if (sheet == null) return list;

            // 获取表头行
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return list;

            List<String> headers = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(); // 用于安全地将 Cell 转换为 String

            // 解析表头
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }

            // 解析数据行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (list.size() >= rowLimit) break;
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> rowMap = new LinkedHashMap<>(); // 使用 LinkedHashMap 保持顺序
                boolean isEmptyRow = true;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    // 处理cell为null的情况，避免NullPointerException
                    String val = cell != null ? formatter.formatCellValue(cell).trim() : "";
                    rowMap.put(headers.get(j), val);
                    if (!val.isEmpty()) {
                        isEmptyRow = false;
                    }
                }
                // 只有非空行才添加到结果列表中
                if (!isEmptyRow) {
                    list.add(rowMap);
                }
            }
        }
        return list;
    }

    /**
     * 解析 CSV 文件
     */
    private List<Map<String, String>> parseCsvFile(InputStream inputStream) throws Exception {
        List<Map<String, String>> list = new ArrayList<>();
        int rowLimit = uploadRowLimit();
        // 使用 UTF-8 读取
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            List<String> headers = null;
            int rowIndex = 0;

            while ((line = br.readLine()) != null) {
                // 处理 BOM 头 (Excel 保存的 CSV 可能带有 BOM)
                if (rowIndex == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                
                List<String> parsed = parseCsvLine(line);
                
                if (rowIndex == 0) {
                    headers = new ArrayList<>();
                    for (String v : parsed) {
                        headers.add(cleanCsvField(v));
                    }
                } else {
                    if (headers != null) {
                        Map<String, String> rowMap = new LinkedHashMap<>();
                        boolean emptyRow = true;
                        for (int i = 0; i < headers.size(); i++) {
                            String val = (i < parsed.size()) ? cleanCsvField(parsed.get(i)) : "";
                            rowMap.put(headers.get(i), val);
                            if (!val.isEmpty()) {
                                emptyRow = false;
                            }
                        }
                        if (!emptyRow) {
                            list.add(rowMap);
                            if (list.size() >= rowLimit) {
                                break;
                            }
                        }
                    }
                }
                rowIndex++;
            }
        }
        return list;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        fields.add(current.toString());
        return fields;
    }

    private String cleanCsvField(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    private String buildCsvContent(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<String> headers = rows.get(0).keySet().stream()
                .filter(k -> !"key".equals(k) && !k.startsWith("__"))
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append("\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                Object val = row.get(headers.get(i));
                String cell = val == null ? "" : String.valueOf(val);
                cell = cell.replace(" ⚠️", "").replace("⚠️", "").trim();
                boolean needQuote = cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r");
                if (needQuote) {
                    cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                }
                sb.append(cell);
                if (i < headers.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String sha256Hex(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String piece = Integer.toHexString(0xff & b);
            if (piece.length() == 1) {
                hex.append('0');
            }
            hex.append(piece);
        }
        return hex.toString();
    }

    private List<Map<String, String>> parseCsvContent(String csvContent) {
        List<Map<String, String>> list = new ArrayList<>();
        String[] lines = csvContent.split("\\r?\\n");
        List<String> headers = null;
        int rowIndex = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (rowIndex == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            List<String> cells = parseCsvLine(line);
            if (headers == null) {
                headers = new ArrayList<>();
                for (String c : cells) {
                    headers.add(cleanCsvField(c));
                }
                rowIndex++;
                continue;
            }
            Map<String, String> rowMap = new LinkedHashMap<>();
            boolean isEmpty = true;
            for (int i = 0; i < headers.size(); i++) {
                String value = i < cells.size() ? cleanCsvField(cells.get(i)) : "";
                rowMap.put(headers.get(i), value);
                if (!value.isEmpty()) {
                    isEmpty = false;
                }
            }
            if (!isEmpty) {
                list.add(rowMap);
            }
            rowIndex++;
        }
        return list;
    }

    @PostMapping("/process")
    public Result<PreprocessingResponse> processData(@RequestBody PreprocessingRequest request) {
        try {
            PreprocessingResponse response = dataProcessingService.processData(request);
            preprocessHistoryService.recordSuccess(request == null ? null : request.getRunId(),
                    response != null && response.getProcessedData() != null ? response.getProcessedData().size() : 0);
            return Result.success(response, "数据处理成功");
        } catch (Exception e) {
            log.error("数据处理异常", e);
            preprocessHistoryService.recordFailure(request == null ? null : request.getRunId(), e.getMessage());
            return Result.error("数据处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/spec")
    public Result<Map<String, Object>> getIndustrialDataSpec() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", "v1");
        payload.put("description", "工业数据字段契约与范围基线");
        payload.put("parameters", IndustrialDataContract.orderedSpecs());
        return Result.success(payload, "获取工业数据契约成功");
    }

    @GetMapping("/quality-metrics")
    public Result<Map<String, Object>> getQualityMetrics(@RequestParam(required = false) String furnaceId,
                                                         @RequestParam(required = false, defaultValue = "1000") Integer limit) {
        try {
            int safeLimit = limit == null ? 1000 : Math.max(100, Math.min(limit, 5000));
            List<ProductionData> rows = (furnaceId != null && !furnaceId.isBlank())
                    ? productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId, PageRequest.of(0, safeLimit))
                    : productionDataRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, safeLimit));
            Map<String, Object> metrics = simulationQualityService.evaluate(rows, furnaceId);
            metrics.put("windowSize", safeLimit);
            return Result.success(metrics, "获取模拟数据质量指标成功");
        } catch (Exception e) {
            log.error("获取模拟数据质量指标失败", e);
            return Result.error("获取模拟数据质量指标失败: " + e.getMessage());
        }
    }

    @PostMapping("/snapshot/export")
    public Result<Map<String, Object>> exportSnapshot(@RequestBody Map<String, Object> request) {
        try {
            List<Map<String, Object>> processedData = (List<Map<String, Object>>) request.get("processedData");
            if (processedData == null || processedData.isEmpty()) {
                return Result.error("没有可导出的快照数据");
            }
            Map<String, Object> fileInfo = request.get("fileInfo") instanceof Map
                    ? (Map<String, Object>) request.get("fileInfo")
                    : new HashMap<>();
            Map<String, Object> preprocessingConfig = request.get("preprocessingConfig") instanceof Map
                    ? (Map<String, Object>) request.get("preprocessingConfig")
                    : new HashMap<>();
            Map<String, Object> headerMapping = fileInfo.get("headerMapping") instanceof Map
                    ? (Map<String, Object>) fileInfo.get("headerMapping")
                    : new HashMap<>();
            String sourceFileName = String.valueOf(fileInfo.getOrDefault("fileName", "snapshot_source.csv"));
            String csvFileName = sourceFileName.replaceAll("\\.[^.]+$", "") + "_snapshot.csv";
            String snapshotId = "snapshot_" + System.currentTimeMillis();
            String generatedAt = Instant.now().toString();
            String contractVersion = String.valueOf(request.getOrDefault("contractVersion", "v1"));

            String csvContent = buildCsvContent(processedData);
            String csvSha256 = sha256Hex(csvContent);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("snapshotVersion", "v1");
            snapshot.put("snapshotId", snapshotId);
            snapshot.put("generatedAt", generatedAt);
            snapshot.put("contractVersion", contractVersion);
            snapshot.put("sourceFileName", sourceFileName);
            snapshot.put("headerMapping", headerMapping);
            snapshot.put("preprocessingConfig", preprocessingConfig);
            Map<String, Object> csv = new LinkedHashMap<>();
            csv.put("fileName", csvFileName);
            csv.put("sha256", csvSha256);
            csv.put("content", csvContent);
            snapshot.put("csv", csv);

            String snapshotFileName = snapshotId + ".snapshot.json";
            String snapshotContent = objectMapper.writeValueAsString(snapshot);
            Map<String, Object> resp = new HashMap<>();
            resp.put("snapshotId", snapshotId);
            resp.put("snapshotFileName", snapshotFileName);
            resp.put("snapshotContent", snapshotContent);
            resp.put("csvSha256", csvSha256);
            resp.put("recordCount", processedData.size());
            return Result.success(resp, "快照导出成功");
        } catch (Exception e) {
            log.error("导出快照失败", e);
            return Result.error("导出快照失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/snapshot/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> importSnapshot(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("快照文件为空");
            }
            if (file.getSize() > SNAPSHOT_UPLOAD_LIMIT_BYTES) {
                return Result.error("快照文件过大");
            }
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            Map<String, Object> snapshot = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> csv = snapshot.get("csv") instanceof Map
                    ? (Map<String, Object>) snapshot.get("csv")
                    : null;
            if (csv == null) {
                return Result.error("快照格式错误：缺少csv内容");
            }
            String csvContent = String.valueOf(csv.getOrDefault("content", ""));
            String expectedSha = String.valueOf(csv.getOrDefault("sha256", ""));
            if (csvContent.isBlank()) {
                return Result.error("快照CSV内容为空");
            }
            String actualSha = sha256Hex(csvContent);
            if (!expectedSha.isBlank() && !expectedSha.equalsIgnoreCase(actualSha)) {
                return Result.error("快照校验失败：CSV哈希不一致");
            }

            List<Map<String, String>> rows = parseCsvContent(csvContent);
            if (rows.isEmpty()) {
                return Result.error("快照中没有有效数据行");
            }
            int rowLimit = uploadRowLimit();
            if (rows.size() > rowLimit) {
                rows = new ArrayList<>(rows.subList(0, rowLimit));
            }
            List<Map<String, String>> previewRows = rows.size() > PREVIEW_LIMIT ? rows.subList(0, PREVIEW_LIMIT) : rows;

            String snapshotId = String.valueOf(snapshot.getOrDefault("snapshotId", "snapshot_unknown"));
            String normalizedSnapshotId = snapshotId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String fileId = normalizedSnapshotId + "__" + UUID.randomUUID();
            uploadedFileService.saveUploadedData(fileId, rows);
            UploadedDataNormalizer.NormalizedUpload normalizedUpload = UploadedDataNormalizer.normalize(rows);
            uploadedFileService.saveNormalizedData(
                    fileId,
                    new UploadedFileService.UploadedFileNormalized(
                            normalizedUpload.rows(),
                            normalizedUpload.originalHeaders(),
                            normalizedUpload.normalizedHeaders(),
                            normalizedUpload.headerMapping()
                    )
            );

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("fileId", fileId);
            fileInfo.put("fileName", String.valueOf(csv.getOrDefault("fileName", "snapshot.csv")));
            fileInfo.put("recordCount", rows.size());
            fileInfo.put("previewLimit", PREVIEW_LIMIT);
            fileInfo.put("headers", normalizedUpload.originalHeaders());
            fileInfo.put("normalizedHeaders", normalizedUpload.normalizedHeaders());
            fileInfo.put("headerMapping", normalizedUpload.headerMapping());

            Map<String, Object> resp = new HashMap<>();
            resp.put("id", fileId);
            resp.put("data", previewRows);
            resp.put("fileInfo", fileInfo);
            resp.put("snapshotId", snapshotId);
            resp.put("contractVersion", snapshot.get("contractVersion"));
            return Result.success(resp, "快照导入成功");
        } catch (Exception e) {
            log.error("导入快照失败", e);
            return Result.error("导入快照失败: " + e.getMessage());
        }
    }

    @PostMapping("/update-file")
    public Result<Map<String, Object>> updateFile(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> fileInfo = (Map<String, Object>) request.get("fileInfo");
            List<Map<String, Object>> processedData = (List<Map<String, Object>>) request.get("processedData");

            if (fileInfo == null || processedData == null || processedData.isEmpty()) {
                return Result.error("参数不完整");
            }

            String fileName = (String) fileInfo.get("fileName");
            log.info("开始更新文件: {}, 数据行数: {}", fileName, processedData.size());

            // 使用 StringBuilder 构建 CSV 内容
            StringBuilder csvContent = new StringBuilder();

            // 获取表头
            Map<String, Object> firstRow = processedData.get(0);
            List<String> headers = new ArrayList<>(firstRow.keySet());

            // 写入表头
            csvContent.append(String.join(",", headers)).append("\n");

            // 写入数据
            for (Map<String, Object> row : processedData) {
                for (int i = 0; i < headers.size(); i++) {
                    Object val = row.get(headers.get(i));
                    csvContent.append(val != null ? val.toString() : "");
                    if (i < headers.size() - 1) csvContent.append(",");
                }
                csvContent.append("\n");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("fileName", fileName);
            response.put("csvContent", csvContent.toString());
            response.put("updatedRows", processedData.size());

            return Result.success(response, "文件更新成功");
        } catch (Exception e) {
            log.error("文件更新失败", e);
            return Result.error("文件更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<ProductionData>> getDataList(@RequestParam(required = false) String furnaceId,
                                                    @RequestParam(required = false) String startDate,
                                                    @RequestParam(required = false) String endDate,
                                                    @RequestParam(required = false) String startTime,
                                                    @RequestParam(required = false) String endTime,
                                                    @RequestParam(required = false) Integer limit) {
        try {
            String effectiveStart = (startDate != null && !startDate.isEmpty()) ? startDate : startTime;
            String effectiveEnd = (endDate != null && !endDate.isEmpty()) ? endDate : endTime;
            int effectiveLimit = (limit == null || limit <= 0) ? DATA_LIST_MAX_LIMIT : Math.min(limit, DATA_LIST_MAX_LIMIT);
            log.info("获取数据列表, 参数: furnaceId={}, start={}, end={}, requestedLimit={}, effectiveLimit={}",
                    furnaceId, effectiveStart, effectiveEnd, limit, effectiveLimit);

            Date start = null;
            Date end = null;
            
            // 增强的日期解析逻辑
            if (effectiveStart != null && !effectiveStart.isEmpty()) {
                start = parseDateFlexible(effectiveStart);
            }
            if (effectiveEnd != null && !effectiveEnd.isEmpty()) {
                end = parseDateFlexible(effectiveEnd);
            }

            var pageRequest = PageRequest.of(0, effectiveLimit);
            List<ProductionData> dataList;
            if (furnaceId != null && start != null && end != null) {
                dataList = productionDataRepository.findByFurnaceIdAndTimestampBetweenOrderByTimestampDesc(furnaceId, start, end, pageRequest);
            } else if (furnaceId != null) {
                dataList = productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId, pageRequest);
            } else if (start != null && end != null) {
                dataList = productionDataRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageRequest);
            } else {
                dataList = productionDataRepository.findAllByOrderByTimestampDesc(pageRequest);
            }

            return Result.success(dataList, "获取数据列表成功");
        } catch (Exception e) {
            log.error("获取数据列表失败", e);
            return Result.error("获取数据列表失败: " + e.getMessage());
        }
    }

    // 辅助方法：解析日期，兼容 ISO 格式和普通 yyyy-MM-dd HH:mm:ss
    private Date parseDateFlexible(String dateStr) {
        try {
            return Date.from(Instant.parse(dateStr)); // 尝试 ISO 8601
        } catch (Exception e) {
            try {
                // 如果不是 ISO，可以尝试其他格式，或者直接抛出
                // 这里简单演示，实际建议使用 DateUtil 工具类
                 java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                 return sdf.parse(dateStr);
            } catch (Exception ex) {
                log.warn("日期解析失败: {}", dateStr);
                return null;
            }
        }
    }
    
    /**
     * 获取上传的文件数据，用于模型训练
     */
    @GetMapping("/uploaded-data/{fileId}")
    public Result<List<Map<String, String>>> getUploadedData(@PathVariable String fileId) {
        try {
            List<Map<String, String>> data = uploadedFileService.getUploadedData(fileId);
            if (data == null) {
                return Result.error("文件数据不存在或已过期");
            }
            log.info("获取上传文件数据，fileId: {}, 数据行数: {}", fileId, data.size());
            return Result.success(data, "获取文件数据成功");
        } catch (Exception e) {
            log.error("获取上传文件数据失败", e);
            return Result.error("获取文件数据失败: " + e.getMessage());
        }
    }

    @Autowired
    private com.blastfurnace.backend.service.CollectionTaskService collectionTaskService;

    @GetMapping("/latest")
    public Result<Map<String, Object>> getLatestData(@RequestParam(required = false) String furnaceId,
                                                     @RequestParam(required = false, defaultValue = "false") Boolean strictLive) {
        try {
            // 优先从数据库获取最新数据
            List<ProductionData> list = furnaceId == null || furnaceId.isBlank()
                    ? productionDataRepository.findAllByOrderByTimestampDesc()
                    : productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId);
            
            ProductionData latest = list == null || list.isEmpty() ? null : list.get(0);
            String source = "LIVE";
            boolean stale = false;
            Date now = new Date();
            Date dataTime = latest == null ? null : latest.getTimestamp();
            boolean strict = Boolean.TRUE.equals(strictLive);
            
            // 如果数据库为空，或者数据过于陈旧（例如超过1分钟），则尝试调用仿真引擎生成预览数据
            if (latest == null || dataTime == null || (now.getTime() - dataTime.getTime() > 60000)) {
                stale = true;
                if (!strict) {
                    try {
                         // 调用仿真引擎生成即时数据（不存库，仅预览）
                         // 传入 furnaceId (如果为空则随机)
                         latest = collectionTaskService.generateDeterministicMockData(furnaceId);
                         source = "MOCK";
                         dataTime = latest == null ? null : latest.getTimestamp();
                    } catch (Exception e) {
                        System.err.println("预览数据生成失败: " + e.getMessage());
                    }
                }
            }
            if (strict && latest == null) {
                source = "NO_LIVE_DATA";
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", latest);
            payload.put("source", source);
            payload.put("serverTime", now);
            payload.put("dataTime", dataTime);
            payload.put("stale", stale);
            payload.put("dataFresh", !"MOCK".equals(source) && !stale);
            payload.put("strictLive", strict);
            return Result.success(payload, "获取最新生产数据成功");
        } catch (Exception e) {
            return Result.error("获取最新生产数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/recent")
    public Result<List<ProductionData>> getRecentData(@RequestParam(required = false) String furnaceId,
                                                      @RequestParam(required = false, defaultValue = "60") Integer limit) {
        try {
            int safeLimit = limit == null ? 60 : Math.max(1, Math.min(limit, 500));
            List<ProductionData> list;
            if (furnaceId != null && !furnaceId.isBlank()) {
                list = productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId, PageRequest.of(0, safeLimit));
            } else {
                list = productionDataRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, safeLimit));
            }
            return Result.success(list, "获取最近数据成功");
        } catch (Exception e) {
            return Result.error("获取最近数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/candidates")
    public Result<List<ProductionData>> getCandidates(@RequestParam(required = false) String furnaceId) {
        try {
            List<ProductionData> list;
            if (furnaceId != null && !furnaceId.isBlank()) {
                list = productionDataRepository.findTop50ByFurnaceIdOrderByTimestampDesc(furnaceId);
            } else {
                list = productionDataRepository.findAllByOrderByTimestampDesc();
                if (list.size() > 50) {
                    list = list.subList(0, 50);
                }
            }
            return Result.success(list, "获取方案候选成功");
        } catch (Exception e) {
            return Result.error("获取方案候选失败: " + e.getMessage());
        }
    }

    @GetMapping("/schemes")
    public Result<List<ProductionData>> getSchemeCandidates(@RequestParam(required = false) String furnaceId,
                                                            @RequestParam(required = false) String startDate,
                                                            @RequestParam(required = false) String endDate,
                                                            @RequestParam(required = false) Integer limit) {
        try {
            Date start = parseDate(startDate);
            Date end = parseDate(endDate);
            List<ProductionData> list;
            if (furnaceId != null && !furnaceId.isBlank() && start != null && end != null) {
                list = productionDataRepository.findByFurnaceIdAndTimestampBetween(furnaceId, start, end);
            } else if (furnaceId != null && !furnaceId.isBlank()) {
                list = productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId);
            } else if (start != null && end != null) {
                list = productionDataRepository.findByTimestampBetween(start, end);
            } else {
                list = productionDataRepository.findAllByOrderByTimestampDesc();
            }
            int max = limit != null && limit > 0 ? limit : 50;
            if (list.size() > max) {
                list = list.subList(0, max);
            }
            return Result.success(list, "获取方案候选成功");
        } catch (Exception e) {
            return Result.error("获取方案候选失败: " + e.getMessage());
        }
    }

    private Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.from(Instant.parse(value));
        } catch (Exception ignored) {
        }
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd"};
        for (String pattern : patterns) {
            try {
                return new java.text.SimpleDateFormat(pattern).parse(value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
