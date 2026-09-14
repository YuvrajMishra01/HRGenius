package com.hrgenius.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * A4-landscape tabular report PDF (Phase 15), built on OpenPDF — the
 * maintained LGPL/MPL fork of iText 4. One reusable look for every export:
 * title, generation subtitle, striped-free plain grid with grey header row.
 */
public final class ReportPdfBuilder {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.GRAY);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 9);

    private ReportPdfBuilder() {
    }

    public static byte[] build(String title, String subtitle, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph heading = new Paragraph(title, TITLE);
            heading.setSpacingAfter(2);
            doc.add(heading);
            Paragraph sub = new Paragraph(subtitle, SUBTITLE);
            sub.setSpacingAfter(10);
            doc.add(sub);

            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            headers.forEach(h -> {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER));
                cell.setBackgroundColor(new Color(238, 238, 238));
                cell.setPadding(5);
                cell.setBorderWidth(0.6f);
                table.addCell(cell);
            });
            rows.forEach(row -> row.forEach(value -> {
                PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, CELL));
                cell.setPadding(4);
                cell.setBorderWidth(0.4f);
                table.addCell(cell);
            }));
            doc.add(table);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }
}
