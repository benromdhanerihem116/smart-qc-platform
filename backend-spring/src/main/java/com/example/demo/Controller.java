package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class Controller {

    @Autowired
    private AuditRepository auditRepository;
    @Autowired
    private PdfService pdfService;

    // Dossier de stockage des images preuves
    private static final String UPLOAD_DIR = "uploads/";

    @PostMapping("/upload")
    public ResponseEntity<?> handleFileUpload(@RequestParam("image") MultipartFile file) throws IOException {
        String urlPython = "http://ai-service:5000/ai-predict";
        RestTemplate restTemplate = new RestTemplate();

        // 1. SAUVEGARDE PHYSIQUE DE L'IMAGE
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            // On écrase si le fichier existe déjà pour la démo
            Path filePath = uploadPath.resolve(file.getOriginalFilename());
            Files.write(filePath, file.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erreur de sauvegarde image: " + e.getMessage());
        }

        // 2. PRÉPARATION POUR L'IA
        ByteArrayResource fileAsResource = new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", fileAsResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // Appel Python
            ResponseEntity<Map> response = restTemplate.exchange(
                    urlPython, HttpMethod.POST, requestEntity, Map.class
            );

            Map<String, Object> responseBody = response.getBody();

            InspectionAudit audit = new InspectionAudit(
                    file.getOriginalFilename(),
                    (String) responseBody.get("status"),
                    (Boolean) responseBody.getOrDefault("is_defective", false),
                    Double.valueOf(responseBody.getOrDefault("prediction", 0).toString())
            );

            InspectionAudit savedAudit = auditRepository.save(audit);

            if (responseBody != null) {
                responseBody.put("auditId", savedAudit.getId());
            }

            return ResponseEntity.ok(responseBody);

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur interne : " + e.getMessage());
        }
    }

    @GetMapping("/audits")
    public java.util.List<InspectionAudit> getRecentAudits() {
        return auditRepository.findTop10ByOrderByScanDateDesc();
    }

    @GetMapping("/audit/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        InspectionAudit audit = auditRepository.findById(id).orElseThrow();
        // On passe le chemin complet si besoin, ou juste l'objet audit
        byte[] pdfContent = pdfService.generateReport(audit);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("filename", "certificat_" + id + ".pdf");

        return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);
    }
}