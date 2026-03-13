package com.blastfurnace.backend.service;

import com.blastfurnace.backend.dto.ReportGenerationDTO;
import com.blastfurnace.backend.dto.ReportGenerationResponseDTO;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.model.ReportHistory;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.repository.ReportHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportGenerationService {
    private final ProductionDataRepository productionDataRepository;
    private final ReportHistoryRepository reportHistoryRepository;
    private final SysConfigService sysConfigService;

    public ReportGenerationResponseDTO generateReport(ReportGenerationDTO request) {
        Date startDate = parseDateTime(request.getStartDate());
        Date endDate = parseDateTime(request.getEndDate());
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("日期解析失败，请确保格式为 yyyy-MM-dd HH:mm:ss");
        }
        String effectiveTimeGrain = resolveEffectiveTimeGrain(request.getReportType(), request.getTimeGrain());
        String format = normalizeFormat(request.getFormat());
        if (!"excel".equals(format) && !"csv".equals(format)) {
            throw new IllegalArgumentException("当前仅支持 Excel 和 CSV 导出");
        }

        List<ProductionData> rawData = productionDataRepository.findByFurnaceIdAndTimestampBetween(
                request.getFurnaceId(), startDate, endDate
        );
        if (rawData == null || rawData.isEmpty()) {
            throw new IllegalArgumentException("该时间段内无数据，无法生成报表");
        }

        List<String> metrics = request.getMetrics();
        if (metrics == null || metrics.isEmpty()) {
            metrics = List.of("temperature");
        }
        List<RowData> rows = toRows(rawData, effectiveTimeGrain, metrics);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("无可导出的有效数据");
        }
        boolean includeTrendChart = Boolean.TRUE.equals(request.getIncludeCharts()) || Boolean.TRUE.equals(request.getIncludeTrendChart());
        boolean includeDistributionChart = Boolean.TRUE.equals(request.getIncludeCharts()) || Boolean.TRUE.equals(request.getIncludeDistributionChart());

        Path outputDir = resolveOutputDirectory(request.getStoragePath());
        ensureDirectory(outputDir);
        String extension = "csv".equals(format) ? ".csv" : ".xlsx";
        String filePrefix = sanitizeFilePrefix(request.getCustomFileName(), request.getFurnaceId());
        Path outputFile = resolveOutputFile(outputDir, filePrefix, extension, Boolean.TRUE.equals(request.getOverwrite()));

        if ("csv".equals(format)) {
            writeCsv(outputFile, rows, metrics);
        } else {
            writeXlsx(outputFile, rows, metrics, includeTrendChart, includeDistributionChart);
        }

        ReportHistory history = new ReportHistory();
        history.setReportName(outputFile.getFileName().toString());
        history.setFileName(outputFile.getFileName().toString());
        history.setFilePath(outputFile.toAbsolutePath().toString());
        history.setFileSize(outputFile.toFile().length());
        history.setReportType(request.getReportType());
        history.setCreateTime(new Date());
        history.setCreator("System");
        StringJoiner params = new StringJoiner(",");
        params.add("format=" + format);
        params.add("timeGrain=" + request.getTimeGrain());
        params.add("effectiveTimeGrain=" + effectiveTimeGrain);
        params.add("trendChart=" + includeTrendChart);
        params.add("distributionChart=" + includeDistributionChart);
        history.setQueryParams(params.toString());
        history = reportHistoryRepository.save(history);

        ReportGenerationResponseDTO response = new ReportGenerationResponseDTO();
        response.setReportId(history.getId());
        response.setFileName(history.getFileName());
        response.setReportFormat(format);
        response.setEffectiveTimeGrain(effectiveTimeGrain);
        response.setStoragePath(outputDir.toAbsolutePath().toString());
        response.setDownloadUrl("/api/report/history/" + history.getId() + "/download");
        return response;
    }

    private Date parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> patterns = List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd");
        for (String pattern : patterns) {
            try {
                return formatter(pattern).parse(value.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String resolveEffectiveTimeGrain(String reportType, String timeGrain) {
        String grain = (timeGrain == null || timeGrain.isBlank()) ? "raw" : timeGrain.trim();
        String type = reportType == null ? "" : reportType.trim().toLowerCase(Locale.ROOT);
        if ("raw".equals(grain) && Arrays.asList("daily", "weekly", "monthly").contains(type)) {
            return "1d";
        }
        return grain;
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "excel";
        }
        String lower = format.trim().toLowerCase(Locale.ROOT);
        if ("xlsx".equals(lower)) {
            return "excel";
        }
        return lower;
    }

    private Path resolveOutputDirectory(String storagePath) {
        Path root = Paths.get(System.getProperty("user.dir"), "data", "reports").toAbsolutePath().normalize();
        if (storagePath == null || storagePath.isBlank()) {
            return root;
        }
        String subDir = storagePath.trim().replace("\\", "/");
        if (subDir.contains("..") || subDir.contains(":")) {
            throw new IllegalArgumentException("存储路径仅允许填写报表根目录下的子目录");
        }
        Path resolved = root.resolve(subDir).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("存储路径超出允许范围");
        }
        return resolved;
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建存储目录: " + directory, e);
        }
    }

    private Path resolveOutputFile(Path dir, String filePrefix, String extension, boolean overwrite) {
        Path file = dir.resolve(filePrefix + extension);
        if (overwrite) {
            return file;
        }
        int i = 1;
        while (Files.exists(file)) {
            file = dir.resolve(filePrefix + "_" + i + extension);
            i++;
        }
        return file;
    }

    private String sanitizeFilePrefix(String customFileName, String furnaceId) {
        String systemName = sysConfigService.getString("system_name", "blast-furnace");
        String defaultPrefix = systemName + "_Report_" + furnaceId;
        String base = (customFileName == null || customFileName.isBlank()) ? defaultPrefix : customFileName.trim();
        return base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private SimpleDateFormat formatter(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(TimeZone.getTimeZone(resolveSystemZoneId()));
        return sdf;
    }

    private ZoneId resolveSystemZoneId() {
        String zone = sysConfigService.getString("system_timezone", "Asia/Shanghai");
        try {
            return ZoneId.of(zone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private List<RowData> toRows(List<ProductionData> rawData, String grain, List<String> metrics) {
        if ("raw".equals(grain)) {
            return rawData.stream()
                    .map(item -> toRow(item, metrics))
                    .collect(Collectors.toList());
        }
        String pattern = "1d".equals(grain) ? "yyyy-MM-dd" : "yyyy-MM-dd HH";
        SimpleDateFormat sdf = formatter(pattern);
        Map<String, List<ProductionData>> grouped = rawData.stream()
                .filter(item -> item.getTimestamp() != null)
                .collect(Collectors.groupingBy(item -> sdf.format(item.getTimestamp()), LinkedHashMap::new, Collectors.toList()));
        List<RowData> rows = new ArrayList<>();
        grouped.forEach((ts, list) -> rows.add(toAggregatedRow(ts, list, metrics)));
        return rows;
    }

    private RowData toRow(ProductionData data, List<String> metrics) {
        RowData row = new RowData();
        row.timestamp = data.getTimestamp() == null ? "" : formatter("yyyy-MM-dd HH:mm:ss").format(data.getTimestamp());
        row.values = new LinkedHashMap<>();
        for (String metric : metrics) {
            row.values.put(metric, metricValue(data, metric));
        }
        row.furnaceId = data.getFurnaceId();
        row.status = data.getStatus();
        row.operator = data.getOperator();
        return row;
    }

    private RowData toAggregatedRow(String ts, List<ProductionData> list, List<String> metrics) {
        RowData row = new RowData();
        row.timestamp = ts;
        row.values = new LinkedHashMap<>();
        for (String metric : metrics) {
            double avg = list.stream()
                    .map(item -> metricValue(item, metric))
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            row.values.put(metric, avg);
        }
        if (!list.isEmpty()) {
            row.furnaceId = list.get(0).getFurnaceId();
            row.status = list.get(0).getStatus();
            row.operator = list.get(0).getOperator();
        }
        return row;
    }

    private void writeCsv(Path file, List<RowData> rows, List<String> metrics) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            List<String> headers = new ArrayList<>();
            headers.add("时间");
            headers.addAll(metrics.stream().map(this::metricLabel).toList());
            headers.addAll(List.of("高炉ID", "状态", "操作员"));
            writer.write(String.join(",", headers));
            writer.write("\n");
            for (RowData row : rows) {
                List<String> cols = new ArrayList<>();
                cols.add(escapeCsv(row.timestamp));
                for (String metric : metrics) {
                    cols.add(String.valueOf(row.values.getOrDefault(metric, 0.0)));
                }
                cols.add(escapeCsv(row.furnaceId));
                cols.add(escapeCsv(row.status));
                cols.add(escapeCsv(row.operator));
                writer.write(String.join(",", cols));
                writer.write("\n");
            }
        } catch (Exception e) {
            throw new IllegalStateException("CSV 写入失败", e);
        }
    }

    private void writeXlsx(Path file, List<RowData> rows, List<String> metrics, boolean includeTrendChart, boolean includeDistributionChart) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("生产报表");
            Row header = sheet.createRow(0);
            int col = 0;
            header.createCell(col++).setCellValue("时间");
            for (String metric : metrics) {
                header.createCell(col++).setCellValue(metricLabel(metric));
            }
            header.createCell(col++).setCellValue("高炉ID");
            header.createCell(col++).setCellValue("状态");
            header.createCell(col).setCellValue("操作员");

            for (int i = 0; i < rows.size(); i++) {
                RowData rowData = rows.get(i);
                Row row = sheet.createRow(i + 1);
                int c = 0;
                row.createCell(c++).setCellValue(rowData.timestamp);
                for (String metric : metrics) {
                    row.createCell(c++).setCellValue(rowData.values.getOrDefault(metric, 0.0));
                }
                row.createCell(c++).setCellValue(rowData.furnaceId == null ? "" : rowData.furnaceId);
                row.createCell(c++).setCellValue(rowData.status == null ? "" : rowData.status);
                row.createCell(c).setCellValue(rowData.operator == null ? "" : rowData.operator);
            }
            autoSizeColumns(sheet, metrics.size() + 4);
            if (includeTrendChart) {
                appendTrendChartSheet(workbook, rows, metrics);
            }
            if (includeDistributionChart) {
                appendDistributionChartSheet(workbook, rows, metrics);
            }
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                workbook.write(out);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Excel 写入失败", e);
        }
    }

    private void appendTrendChartSheet(XSSFWorkbook workbook, List<RowData> rows, List<String> metrics) {
        if (metrics == null || metrics.isEmpty() || rows == null || rows.isEmpty()) {
            return;
        }
        List<RowData> sampledRows = sampleRows(rows, 300);
        if (sampledRows.isEmpty()) {
            return;
        }
        XSSFSheet sheet = workbook.createSheet("参数趋势图");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("时间");
        for (int i = 0; i < metrics.size(); i++) {
            header.createCell(i + 1).setCellValue(metricLabel(metrics.get(i)));
        }
        for (int i = 0; i < sampledRows.size(); i++) {
            RowData rowData = sampledRows.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(rowData.timestamp == null ? "" : rowData.timestamp);
            for (int j = 0; j < metrics.size(); j++) {
                Double value = rowData.values == null ? null : rowData.values.get(metrics.get(j));
                row.createCell(j + 1).setCellValue(value == null ? 0.0 : value);
            }
        }
        int lastRow = sampledRows.size();
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFChart chart = drawing.createChart(drawing.createAnchor(0, 0, 0, 0, 0, 2, 16, 26));
        chart.setTitleText("参数趋势图");
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("时间");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("数值");
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        XDDFCategoryDataSource categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(1, lastRow, 0, 0)
        );
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        for (int i = 0; i < metrics.size(); i++) {
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet,
                    new CellRangeAddress(1, lastRow, i + 1, i + 1)
            );
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(categories, values);
            series.setTitle(metricLabel(metrics.get(i)), null);
            series.setMarkerStyle(MarkerStyle.NONE);
            series.setSmooth(false);
        }
        chart.plot(data);
        autoSizeColumns(sheet, metrics.size() + 1);
    }

    private void appendDistributionChartSheet(XSSFWorkbook workbook, List<RowData> rows, List<String> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        XSSFSheet sheet = workbook.createSheet("参数分布图");
        int sectionHeight = 24;
        for (int m = 0; m < metrics.size(); m++) {
            String metric = metrics.get(m);
            List<BinPoint> bins = buildDistribution(rows, metric, 10);
            if (bins.isEmpty()) {
                continue;
            }
            int startRow = m * sectionHeight;
            sheet.createRow(startRow).createCell(0).setCellValue(metricLabel(metric) + " 分布");
            Row header = sheet.createRow(startRow + 1);
            header.createCell(0).setCellValue("区间");
            header.createCell(1).setCellValue("频次");
            for (int i = 0; i < bins.size(); i++) {
                Row row = sheet.createRow(startRow + 2 + i);
                row.createCell(0).setCellValue(bins.get(i).label);
                row.createCell(1).setCellValue(bins.get(i).count);
            }
            int dataStart = startRow + 2;
            int dataEnd = dataStart + bins.size() - 1;
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFChart chart = drawing.createChart(drawing.createAnchor(0, 0, 0, 0, 3, startRow + 1, 13, startRow + 20));
            chart.setTitleText(metricLabel(metric) + " 分布图");
            chart.setTitleOverlay(false);
            XDDFChartLegend legend = chart.getOrAddLegend();
            legend.setPosition(LegendPosition.TOP_RIGHT);
            XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            bottomAxis.setTitle("区间");
            XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
            leftAxis.setTitle("频次");
            leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
            XDDFCategoryDataSource categories = XDDFDataSourcesFactory.fromStringCellRange(
                    sheet,
                    new CellRangeAddress(dataStart, dataEnd, 0, 0)
            );
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet,
                    new CellRangeAddress(dataStart, dataEnd, 1, 1)
            );
            XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
            data.setBarDirection(BarDirection.COL);
            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(categories, values);
            series.setTitle(metricLabel(metric), null);
            chart.plot(data);
        }
        autoSizeColumns(sheet, 2);
    }

    private List<RowData> sampleRows(List<RowData> rows, int maxPoints) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() <= maxPoints) {
            return rows;
        }
        List<RowData> sampled = new ArrayList<>();
        double step = (double) rows.size() / maxPoints;
        for (int i = 0; i < maxPoints; i++) {
            int idx = Math.min(rows.size() - 1, (int) Math.floor(i * step));
            sampled.add(rows.get(idx));
        }
        return sampled;
    }

    private List<BinPoint> buildDistribution(List<RowData> rows, String metric, int bucketCount) {
        List<Double> values = rows.stream()
                .map(row -> row.values == null ? null : row.values.get(metric))
                .filter(Objects::nonNull)
                .filter(v -> !v.isNaN() && !v.isInfinite())
                .collect(Collectors.toList());
        if (values.isEmpty()) {
            return List.of();
        }
        double min = values.stream().mapToDouble(v -> v).min().orElse(0.0);
        double max = values.stream().mapToDouble(v -> v).max().orElse(0.0);
        if (Math.abs(max - min) < 1e-9) {
            List<BinPoint> single = new ArrayList<>();
            single.add(new BinPoint(String.format(Locale.ROOT, "%.2f", min), (double) values.size()));
            return single;
        }
        int size = Math.max(5, bucketCount);
        double step = (max - min) / size;
        int[] counts = new int[size];
        for (Double value : values) {
            int index = (int) ((value - min) / step);
            if (index >= size) {
                index = size - 1;
            }
            if (index < 0) {
                index = 0;
            }
            counts[index]++;
        }
        List<BinPoint> bins = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double start = min + i * step;
            double end = start + step;
            String label = String.format(Locale.ROOT, "%.2f-%.2f", start, end);
            bins.add(new BinPoint(label, (double) counts[i]));
        }
        return bins;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            int capped = Math.min(width + 512, 12000);
            sheet.setColumnWidth(i, capped);
        }
    }

    private String metricLabel(String metric) {
        return switch (metric) {
            case "temperature" -> "温度(°C)";
            case "pressure" -> "压力(kPa)";
            case "windVolume" -> "风量";
            case "coalInjection" -> "喷煤量";
            case "materialHeight" -> "料面高度";
            case "gasFlow" -> "煤气流量";
            case "oxygenLevel" -> "氧气含量";
            case "productionRate" -> "生产速率";
            case "energyConsumption" -> "能耗";
            case "hotMetalTemperature" -> "铁水温度(°C)";
            case "constantSignal" -> "恒定信号";
            case "siliconContent" -> "硅含量";
            default -> metric;
        };
    }

    private Double metricValue(ProductionData data, String metric) {
        return switch (metric) {
            case "temperature" -> data.getTemperature();
            case "pressure" -> data.getPressure();
            case "windVolume" -> data.getWindVolume();
            case "coalInjection" -> data.getCoalInjection();
            case "materialHeight" -> data.getMaterialHeight();
            case "gasFlow" -> data.getGasFlow();
            case "oxygenLevel" -> data.getOxygenLevel();
            case "productionRate" -> data.getProductionRate();
            case "energyConsumption" -> data.getEnergyConsumption();
            case "hotMetalTemperature" -> data.getHotMetalTemperature();
            case "constantSignal" -> data.getConstantSignal();
            case "siliconContent" -> data.getSiliconContent();
            default -> null;
        };
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static class RowData {
        private String timestamp;
        private Map<String, Double> values;
        private String furnaceId;
        private String status;
        private String operator;
    }

    private static class BinPoint {
        private final String label;
        private final double count;

        private BinPoint(String label, double count) {
            this.label = label;
            this.count = count;
        }
    }
}
