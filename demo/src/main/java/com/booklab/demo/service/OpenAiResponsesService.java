package com.booklab.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
public class OpenAiResponsesService {

    public record ExtractTranslateResult(String hebrewPlain, String hebrewNikud, String frText) {}

    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public OpenAiResponsesService(
            @Value("${app.openai.model:gpt-5-mini}") String model,
            @Value("${app.openai.apiKey:}") String apiKeyProp
    ) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        String env = System.getenv("OPENAI_API_KEY");
        this.apiKey = (apiKeyProp != null && !apiKeyProp.isBlank()) ? apiKeyProp : env;

        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY manquant (env) ou app.openai.apiKey (properties/yml).");
        }

        this.model = model;
    }

    public ExtractTranslateResult extractTranslateFromImage(Path imagePath) throws Exception {
        byte[] bytes = Files.readAllBytes(imagePath);
        String base64 = Base64.getEncoder().encodeToString(bytes);

        String mime = guessMime(imagePath);
        String dataUrl = "data:" + mime + ";base64," + base64;

        String instructions =
                "Tu es un expert en hébreu (textes religieux) et en traduction française.\n" +
                "Objectif: extraire le texte hébreu de l'image, produire une version avec niqqud, et traduire en français.\n" +
                "Réponds STRICTEMENT en JSON valide, sans texte autour.\n" +
                "Clés attendues: hebrew_plain, hebrew_niqqud, french.\n" +
                "Conserve la structure (retours à la ligne). Ne rajoute pas d'explications.";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("instructions", instructions);

        // input = [{role:user, content:[{input_text},{input_image}]}]
        List<Object> input = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");

        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", "Analyse cette page et retourne le JSON demandé."));
        content.add(Map.of("type", "input_image", "image_url", dataUrl));
        msg.put("content", content);

        input.add(msg);
        payload.put("input", input);

        String out = callResponses(payload);
        JsonNode json = parseJsonObject(out);

        return new ExtractTranslateResult(
                safeText(json, "hebrew_plain"),
                safeText(json, "hebrew_niqqud"),
                safeText(json, "french")
        );
    }

    public ExtractTranslateResult nikudAndTranslateFromText(String hebrewText) throws Exception {
        String instructions =
                "Tu es un expert en hébreu (textes religieux) et en traduction française.\n" +
                "Objectif: à partir d'un texte hébreu, produire une version avec niqqud, et traduire en français.\n" +
                "Réponds STRICTEMENT en JSON valide, sans texte autour.\n" +
                "Clés attendues: hebrew_plain, hebrew_niqqud, french.\n" +
                "Conserve la structure (retours à la ligne). Ne rajoute pas d'explications.";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("instructions", instructions);

        List<Object> input = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", List.of(
                Map.of("type", "input_text", "text",
                        "Texte hébreu:\n" + hebrewText + "\n\nRetourne le JSON demandé.")
        ));
        input.add(msg);
        payload.put("input", input);

        String out = callResponses(payload);
        JsonNode json = parseJsonObject(out);

        return new ExtractTranslateResult(
                safeText(json, "hebrew_plain"),
                safeText(json, "hebrew_niqqud"),
                safeText(json, "french")
        );
    }

    private String callResponses(Map<String, Object> payload) throws Exception {
        String body = om.writeValueAsString(payload);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 300) {
            throw new RuntimeException("OpenAI API error " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = om.readTree(resp.body());

        // D'après la spec Responses, il peut y avoir "output_text"
        if (root.hasNonNull("output_text")) {
            return root.get("output_text").asText();
        }

        // Sinon, on reconstruit depuis output[].content[]
        StringBuilder sb = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode c : content) {
                        if (c.has("type") && "output_text".equals(c.get("type").asText()) && c.has("text")) {
                            sb.append(c.get("text").asText());
                        }
                    }
                }
            }
        }
        String s = sb.toString().trim();
        if (s.isBlank()) {
            throw new RuntimeException("OpenAI: output_text vide (réponse inattendue). Body=" + resp.body());
        }
        return s;
    }

    private JsonNode parseJsonObject(String s) throws Exception {
        String trimmed = s.trim();

        // parfois le modèle renvoie un bloc ```json ... ```
        trimmed = trimmed.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "").trim();

        // si du texte entoure, on extrait le premier { ... }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }

        return om.readTree(trimmed);
    }

    private static String safeText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? "" : v.asText("");
    }

    private static String guessMime(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
