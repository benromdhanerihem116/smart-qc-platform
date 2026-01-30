package com.example.demo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.security.MessageDigest;

@Service
public class PdfService {

    private static final Color COLOR_PRIMARY = new Color(20, 30, 48);
    private static final Color COLOR_ACCENT = new Color(0, 210, 255);
    private static final Color COLOR_SUCCESS = new Color(0, 200, 83);
    private static final Color COLOR_DANGER = new Color(213, 0, 0);
    private static final Color COLOR_GREY_LIGHT = new Color(240, 240, 240);

    private static final String UPLOAD_DIR = "uploads/";

    @Value("${smartqc.factory.line}")
    private String factoryLine;


    public byte[] generateReport(InspectionAudit audit) {
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new HeaderFooterPageEvent());
            document.open();

            //  1. EN-TÊTE
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 2});

            PdfPCell logoCell = new PdfPCell();
            Paragraph logoText = new Paragraph("SMART-QC", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.WHITE));
            logoCell.addElement(logoText);
            logoCell.addElement(new Paragraph("AI PLATFORM", FontFactory.getFont(FontFactory.COURIER, 8, Color.WHITE)));
            logoCell.setBackgroundColor(COLOR_PRIMARY);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            logoCell.setPadding(15);
            logoCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(logoCell);

            PdfPCell titleCell = new PdfPCell();
            Paragraph docTitle = new Paragraph("CERTIFICAT DE CONFORMITÉ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_PRIMARY));
            docTitle.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(docTitle);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String dateStr = audit.getScanDate() != null ? audit.getScanDate().format(formatter) : LocalDateTime.now().format(formatter);
            Paragraph dateP = new Paragraph("Date d'émission : " + dateStr, FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY));
            dateP.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(dateP);

            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setPaddingRight(10);
            headerTable.addCell(titleCell);

            document.add(headerTable);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            //  2. VERDICT
            boolean isOk = !audit.isDefective();
            Color statusColor = isOk ? COLOR_SUCCESS : COLOR_DANGER;
            String statusText = isOk ? "ACCEPTE / COMPLIANT" : "REJETE / DEFECTIVE";
            String subText = isOk ? "La pièce répond aux standards de qualité." : "Défaut critique détecté par l'IA.";

            PdfPTable statusTable = new PdfPTable(1);
            statusTable.setWidthPercentage(100);
            PdfPCell statusCell = new PdfPCell();
            statusCell.setBackgroundColor(statusColor);
            statusCell.setPadding(20);
            statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph verdict = new Paragraph(statusText, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.WHITE));
            verdict.setAlignment(Element.ALIGN_CENTER);
            statusCell.addElement(verdict);
            Paragraph subVerdict = new Paragraph(subText, FontFactory.getFont(FontFactory.HELVETICA, 12, Color.WHITE));
            subVerdict.setAlignment(Element.ALIGN_CENTER);
            statusCell.addElement(subVerdict);

            statusTable.addCell(statusCell);
            document.add(statusTable);
            document.add(new Paragraph(" "));

            //  3. DÉTAILS AVEC PHOTO
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{1, 2});
            infoTable.setSpacingBefore(10);

            addInfoRow(infoTable, "ID Transaction (UUID)", UUID.randomUUID().toString().toUpperCase());
            addInfoRow(infoTable, "ID Audit Interne", String.valueOf(audit.getId()));


            // Étiquette "Preuve Visuelle"
            PdfPCell labelImage = new PdfPCell(new Paragraph("Preuve Visuelle", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY)));
            labelImage.setBackgroundColor(COLOR_GREY_LIGHT);
            labelImage.setPadding(8);
            labelImage.setVerticalAlignment(Element.ALIGN_MIDDLE);
            labelImage.setBorderColor(Color.LIGHT_GRAY);
            infoTable.addCell(labelImage);

            PdfPCell imageCell = new PdfPCell();
            imageCell.setPadding(10);
            imageCell.setBorderColor(Color.LIGHT_GRAY);

            try {
                // On va chercher l'image dans le dossier uploads
                String imagePath = UPLOAD_DIR + audit.getFilename();

                // Vérification si le fichier existe
                if(Files.exists(Paths.get(imagePath))) {
                    Image img = Image.getInstance(imagePath);
                    img.scaleToFit(250, 200);
                    img.setAlignment(Element.ALIGN_CENTER);

                    imageCell.addElement(img);
                } else {
                    imageCell.addElement(new Paragraph("Image introuvable : " + audit.getFilename()));
                }
            } catch (Exception e) {
                imageCell.addElement(new Paragraph("Erreur chargement image"));
            }
            infoTable.addCell(imageCell);
            addInfoRow(infoTable, "Score de Confiance IA", audit.getConfidence() + " %");
            addInfoRow(infoTable, "Architecture IA", "MobileNetV2 (Fine-Tuned)");
            addInfoRow(infoTable, "Station de Contrôle", factoryLine);

            document.add(infoTable);
            document.add(new Paragraph(" "));

            //  4. FOOTER
            PdfPTable footerDetails = new PdfPTable(1);
            footerDetails.setWidthPercentage(100);

            PdfPCell codeBlock = new PdfPCell();
            codeBlock.setBackgroundColor(COLOR_GREY_LIGHT);
            codeBlock.setPadding(10);
            codeBlock.setBorderColor(Color.LIGHT_GRAY);

            Font codeFont = FontFactory.getFont(FontFactory.COURIER, 8, Color.DARK_GRAY);
            Font boldCode = FontFactory.getFont(FontFactory.COURIER_BOLD, 8, Color.BLACK);

            // Calcul de la vraie signature
            String realSignature = calculateRealSHA256(audit);

            codeBlock.addElement(new Paragraph("DIGITAL SIGNATURE HASH (SHA-256):", boldCode));
            codeBlock.addElement(new Paragraph(realSignature, codeFont));

            // On ajoute un Timestamp pour prouver le moment de la génération du PDF
            codeBlock.addElement(new Paragraph("GENERATION TIMESTAMP: " + System.currentTimeMillis(), codeFont));

            codeBlock.addElement(new Paragraph("\nCe document est généré automatiquement par le système Smart-QC. Toute modification des données invalide ce certificat.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY)));

            footerDetails.addCell(codeBlock);
            document.add(footerDetails);

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.COURIER, 10, Color.BLACK);

        PdfPCell cellLabel = new PdfPCell(new Paragraph(label, labelFont));
        cellLabel.setBackgroundColor(COLOR_GREY_LIGHT);
        cellLabel.setPadding(8);
        cellLabel.setBorderColor(Color.LIGHT_GRAY);
        cellLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPCell cellValue = new PdfPCell(new Paragraph(value, valueFont));
        cellValue.setPadding(8);
        cellValue.setBorderColor(Color.LIGHT_GRAY);
        cellValue.setVerticalAlignment(Element.ALIGN_MIDDLE);

        table.addCell(cellLabel);
        table.addCell(cellValue);
    }

    class HeaderFooterPageEvent extends PdfPageEventHelper {
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase footer = new Phrase("Smart-QC generated report - Page " + writer.getPageNumber(),
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 10, 0);
        }
    }


    private String calculateRealSHA256(InspectionAudit audit) {
        try {
            // 1. On concatène les données critiques qui ne doivent pas changer
            String originalString = "ID:" + audit.getId() +
                    "|STATUS:" + audit.isDefective() +
                    "|DATE:" + audit.getScanDate() +
                    "|CONF:" + audit.getConfidence();

            // 2. On utilise l'algorithme SHA-256 natif de Java
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 3. On convertit les bytes en texte hexadécimal (la longue chaîne de caractères)
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();

        } catch (Exception e) {
            // En cas d'erreur rare, on renvoie un placeholder
            return "ERROR-CALCULATING-HASH";
        }
    }
}