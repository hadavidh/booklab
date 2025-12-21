package com.booklab.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import java.util.Base64;

@Service
public class OpenAiResponsesService {

  public record ExtractTranslateResult(String hebrewPlain, String hebrewNikud, String french) {}

  private final HttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  private final URI endpoint;
  private final String textModel;
  private final String visionModel;
  private final int timeoutSeconds;
  private final int maxOutputTokens;

  public OpenAiResponsesService(
      @Value("${app.openai.endpoint}") String endpoint,
      @Value("${app.openai.text-model}") String textModel,
      @Value("${app.openai.vision-model}") String visionModel,
      @Value("${app.openai.timeout-seconds:120}") int timeoutSeconds,
      @Value("${app.openai.max-output-tokens:3500}") int maxOutputTokens
  ) {
    this.endpoint = URI.create(endpoint);
    this.textModel = textModel;
    this.visionModel = visionModel;
    this.timeoutSeconds = timeoutSeconds;
    this.maxOutputTokens = maxOutputTokens;

    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  /** IMAGE -> JSON: hebrew_plain + hebrew_niqqud + french */
  public ExtractTranslateResult extractNikudAndTranslateFromImage(Path imagePath) {
    try {
      byte[] bytes = Files.readAllBytes(imagePath);
      String mime = detectMime(imagePath);
      String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

      String instruction = """
      Tu es un expert de transcription hébraïque + vocalisation + traduction.
      Retourne un JSON STRICT (aucun texte hors JSON) avec exactement ces clés:
      - hebrew_plain : transcription EXACTE du texte hébreu tel qu'il apparaît (ponctuation + retours à la ligne).
      - hebrew_niqqud : même texte mais avec nekoudot/niqqud. IMPORTANT: si tu n'es pas sûr d'un mot, laisse ce mot SANS voyelles plutôt que d'inventer.
      - french : traduction française fidèle, claire, en conservant la structure (retours à la ligne, citations, numéros). Sans explications.
      """;

      Map<String, Object> message = new LinkedHashMap<>();
      message.put("role", "user");

      List<Map<String, Object>> content = new ArrayList<>();
      content.add(Map.of("type", "input_text", "text", instruction));
      content.add(Map.of("type", "input_image", "image_url", dataUrl));
      message.put("content", content);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", visionModel);
      body.put("input", List.of(message));
      body.put("max_output_tokens", maxOutputTokens);
      body.put("store", false);

      // ✅ Responses API structured outputs JSON mode
      body.put("text", Map.of("format", Map.of("type", "json_object")));

      String out = callResponses(body);
      return parseResultJson(out);

    } catch (Exception e) {
      throw new RuntimeException("Vision extract/translate failed: " + e.getMessage(), e);
    }
  }

  /** TEXT -> JSON: hebrew_plain + hebrew_niqqud + french */
  public ExtractTranslateResult nikudAndTranslateFromText(String hebrewText) {
    if (hebrewText == null || hebrewText.isBlank()) {
      return new ExtractTranslateResult("", "", "");
    }

    String instruction = """
    Tu es un expert hébreu (niqqud) + traducteur (hébreu → français).
    Retourne un JSON STRICT (aucun texte hors JSON) avec exactement ces clés:
    - hebrew_plain : le texte hébreu tel quel (conserve retours à la ligne)
    - hebrew_niqqud : version avec nekoudot/niqqud. IMPORTANT: si tu n'es pas sûr d'un mot, laisse ce mot SANS voyelles plutôt que d'inventer.
    - french : traduction française fidèle, claire, même structure, sans explications.
    TEXTE HÉBREU:
    """ + hebrewText;

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", textModel);
    body.put("input", instruction);
    body.put("max_output_tokens", maxOutputTokens);
    body.put("store", false);

    // ✅ Responses API structured outputs JSON mode
    body.put("text", Map.of("format", Map.of("type", "json_object")));

    String out = callResponses(body);
    return parseResultJson(out);
  }

  private ExtractTranslateResult parseResultJson(String maybeJson) {
    try {
      String candidate = extractFirstJsonObject((maybeJson == null) ? "" : maybeJson.trim());
      JsonNode root = mapper.readTree(candidate);

      String hebPlain = root.path("hebrew_plain").asText("");
      String hebNikud = root.path("hebrew_niqqud").asText("");
      String fr = root.path("french").asText("");

      return new ExtractTranslateResult(hebPlain, hebNikud, fr);

    } catch (Exception e) {
      String head = (maybeJson == null) ? "" : maybeJson.substring(0, Math.min(400, maybeJson.length()));
      throw new RuntimeException("OpenAI JSON parse failed. Output starts with: " + head, e);
    }
  }

  private String extractFirstJsonObject(String s) {
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) return s.substring(start, end + 1);
    return s;
  }

  private String callResponses(Map<String, Object> payload) {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new RuntimeException("OPENAI_API_KEY missing (env var).");
    }

    try {
      String json = mapper.writeValueAsString(payload);

      HttpRequest req = HttpRequest.newBuilder(endpoint)
          .timeout(Duration.ofSeconds(timeoutSeconds))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();

      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      int status = res.statusCode();
      String body = res.body() == null ? "" : res.body();

      if (status < 200 || status >= 300) {
        throw new OpenAiApiException(status, extractErrorMessage(body));
      }

      return extractOutputText(body);

    } catch (OpenAiApiException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("OpenAI request failed: " + e.getMessage(), e);
    }
  }

  private String extractOutputText(String responseJson) throws Exception {
    JsonNode root = mapper.readTree(responseJson);
    JsonNode output = root.path("output");

    StringBuilder sb = new StringBuilder();
    if (output.isArray()) {
      for (JsonNode item : output) {
        if (!"message".equals(item.path("type").asText())) continue;

        JsonNode content = item.path("content");
        if (!content.isArray()) continue;

        for (JsonNode part : content) {
          if ("output_text".equals(part.path("type").asText())) {
            sb.append(part.path("text").asText(""));
          }
        }
      }
    }
    return sb.toString().trim();
  }

  private String extractErrorMessage(String body) {
    try {
      JsonNode root = mapper.readTree(body);
      JsonNode err = root.path("error");
      if (err.isMissingNode()) return body;
      String msg = err.path("message").asText("");
      return msg.isBlank() ? body : msg;
    } catch (Exception ignore) {
      return body;
    }
  }

  private String detectMime(Path p) {
    try {
      String mime = Files.probeContentType(p);
      if (mime != null && !mime.isBlank()) return mime;
    } catch (Exception ignore) {}

    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".png")) return "image/png";
    if (name.endsWith(".webp")) return "image/webp";
    if (name.endsWith(".jpeg")) return "image/jpeg";
    return "image/jpeg";
  }

  public static class OpenAiApiException extends RuntimeException {
    private final int statusCode;
    public OpenAiApiException(int statusCode, String message) {
      super("OpenAI API error " + statusCode + ": " + message);
      this.statusCode = statusCode;
    }
    public int getStatusCode() { return statusCode; }
  }
}
