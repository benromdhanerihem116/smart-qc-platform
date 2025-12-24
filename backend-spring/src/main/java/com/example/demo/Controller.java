package com.example.demo;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class Controller {

    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("image") MultipartFile file) throws IOException {

        // 1. Préparer l'envoi vers Python
        String urlPython = "http://localhost:5000/ai-predict";
        RestTemplate robot = new RestTemplate();

        // 2. Emballer le fichier pour le voyage
        ByteArrayResource fileAsResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", fileAsResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 3. Envoyer à Python et attendre la réponse
        try {
            String response = robot.postForObject(urlPython, requestEntity, String.class);
            return response; // On renvoie la réponse de l'IA directement à React
        } catch (Exception e) {
            return "Erreur lors de l'appel à l'IA: " + e.getMessage();
        }
    }
}