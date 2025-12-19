package com.booklab.demo.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class OpenAiTranslationService {

    private final OpenAIClient client;
    private final String model;

    public OpenAiTranslationService(@Value("${app.openai.model:gpt-4o-mini}") String model) {
        this.client = OpenAIOkHttpClient.fromEnv(); // utilise OPENAI_API_KEY
        this.model = model;
    }

    public String translateHebrewToFrench(String hebrewText) {
        if (hebrewText == null || hebrewText.isBlank()) return "";

        String prompt =
                "Tu es un traducteur expert (hébreu → français) pour des textes religieux.\n" +
                "Consignes:\n" +
                "- Traduction fidèle et claire.\n" +
                "- Conserve les citations, parenthèses, numéros, et la structure (retours à la ligne).\n" +
                "- Ne rajoute pas d'explications.\n" +
                "- Retourne uniquement le texte en français.\n\n" +
                "TEXTE HÉBREU:\n" +
                hebrewText;

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(prompt)
                .build();

        Response response = client.responses().create(params);
        String out = extractOutputText(response);

        return out == null ? "" : out.trim();
    }

    private static String extractOutputText(Response response) {
        if (response == null || response.output() == null) return "";

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)          // ✅ le fix est ici
                .collect(Collectors.joining());
    }
}
