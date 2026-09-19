package com.bjworld21.conference.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxWorkbookWriter {
    private XlsxWorkbookWriter() {
    }

    public static byte[] createWorkbook(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "[Content_Types].xml", contentTypesXml());
            writeEntry(zipOutputStream, "_rels/.rels", rootRelsXml());
            writeEntry(zipOutputStream, "xl/workbook.xml", workbookXml(sheetName));
            writeEntry(zipOutputStream, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            writeEntry(zipOutputStream, "xl/styles.xml", stylesXml());
            writeEntry(zipOutputStream, "xl/worksheets/sheet1.xml", sheetXml(headers, rows));
            zipOutputStream.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create workbook.", e);
        }
    }

    private static void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escapeXml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"2\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2563EB\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FF93C5FD\"/></left><right style=\"thin\"><color rgb=\"FF93C5FD\"/></right><top style=\"thin\"><color rgb=\"FF93C5FD\"/></top><bottom style=\"thin\"><color rgb=\"FF93C5FD\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"1\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"true\"/></xf>"
                + "</cellXfs>"
                + "</styleSheet>";
    }

    private static String sheetXml(List<String> headers, List<List<Object>> rows) {
        int columnCount = Math.max(headers.size(), rows.stream().mapToInt(List::size).max().orElse(0));
        String lastColumn = columnName(Math.max(columnCount - 1, 0));
        int lastRowNumber = rows.size() + 1;
        String dimension = columnCount == 0 ? "A1" : "A1:" + lastColumn + lastRowNumber;

        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        builder.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        builder.append("<dimension ref=\"").append(dimension).append("\"/>");
        builder.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/></sheetView></sheetViews>");
        builder.append("<sheetFormatPr defaultRowHeight=\"20\"/>");
        builder.append(columnsXml(headers, rows));
        builder.append("<sheetData>");
        builder.append(rowXml(1, headers, true));
        for (int index = 0; index < rows.size(); index += 1) {
            builder.append(rowXml(index + 2, rows.get(index), false));
        }
        builder.append("</sheetData>");
        builder.append("<autoFilter ref=\"A1:").append(lastColumn).append(lastRowNumber).append("\"/>");
        builder.append("</worksheet>");
        return builder.toString();
    }

    private static String columnsXml(List<String> headers, List<List<Object>> rows) {
        int columnCount = Math.max(headers.size(), rows.stream().mapToInt(List::size).max().orElse(0));
        if (columnCount == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder("<cols>");
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex += 1) {
            double width = estimateColumnWidth(headers, rows, columnIndex);
            builder.append("<col min=\"").append(columnIndex + 1).append("\" max=\"").append(columnIndex + 1)
                    .append("\" width=\"").append(String.format(Locale.US, "%.2f", width)).append("\" customWidth=\"1\"/>");
        }
        builder.append("</cols>");
        return builder.toString();
    }

    private static double estimateColumnWidth(List<String> headers, List<List<Object>> rows, int columnIndex) {
        int maxLength = 0;
        if (columnIndex < headers.size()) {
            maxLength = Math.max(maxLength, textValue(headers.get(columnIndex)).length());
        }
        for (List<Object> row : rows) {
            if (columnIndex < row.size()) {
                maxLength = Math.max(maxLength, textValue(row.get(columnIndex)).length());
            }
        }
        return Math.min(Math.max(maxLength + 2.0, 12.0), 48.0);
    }

    private static String rowXml(int rowNumber, List<?> values, boolean headerRow) {
        StringBuilder builder = new StringBuilder();
        builder.append("<row r=\"").append(rowNumber).append("\">");
        for (int columnIndex = 0; columnIndex < values.size(); columnIndex += 1) {
            String reference = columnName(columnIndex) + rowNumber;
            builder.append(cellXml(reference, values.get(columnIndex), headerRow));
        }
        builder.append("</row>");
        return builder.toString();
    }

    private static String cellXml(String reference, Object value, boolean headerRow) {
        if (headerRow) {
            return "<c r=\"" + reference + "\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escapeXml(textValue(value)) + "</t></is></c>";
        }
        if (value == null) {
            return "<c r=\"" + reference + "\"/>";
        }
        if (value instanceof Number) {
            return "<c r=\"" + reference + "\"><v>" + value + "</v></c>";
        }
        if (value instanceof Boolean booleanValue) {
            return "<c r=\"" + reference + "\" t=\"b\"><v>" + (booleanValue ? 1 : 0) + "</v></c>";
        }
        return "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escapeXml(textValue(value)) + "</t></is></c>";
    }

    private static String columnName(int columnIndex) {
        StringBuilder builder = new StringBuilder();
        int current = columnIndex + 1;
        while (current > 0) {
            int remainder = (current - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            current = (current - 1) / 26;
        }
        return builder.toString();
    }

    private static String textValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
