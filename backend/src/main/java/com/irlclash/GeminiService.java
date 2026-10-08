package com.irlclash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GeminiService {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${gemini.api-key:}")
    String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    String model;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public List<Map<String, Object>> judge(
            List<Map<String, Object>> missions,
            List<Map<String, Object>> photos
    ) throws Exception {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not configured."
            );
        }

        /*
         * ---------------------------------------------------------
         * 1. Build the judging instructions
         * ---------------------------------------------------------
         */

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                You are the fair AI referee for a two-player outdoor photography game called IRL Clash.

                Judge every submitted photo against the exact mission for its round.

                Rules:
                - Judge only what is visibly present in the image.
                - Never identify or name people.
                - Do not reward dangerous activity.
                - Do not reward traffic or road risk.
                - Do not reward trespassing.
                - Do not reward weapons.
                - Do not reward private or sensitive information.
                - Prefer safe public/outdoor photography.
                - Score every submitted image separately.
                - Give a short description of what is visibly present.
                - Give a short reason explaining the score.

                Scoring:
                90-100 = excellent match
                75-89 = strong match
                55-74 = relevant but ordinary
                30-54 = weak match
                0-29 = failure, irrelevant, or unsafe

                Return ONLY valid JSON in exactly this structure:

                {
                  "reviews": [
                    {
                      "round": 1,
                      "playerId": "player-id",
                      "score": 85,
                      "description": "Short visible description.",
                      "reason": "Why this image received this score."
                    }
                  ]
                }

                Do not include Markdown.
                Do not include ```json.
                Do not include any text outside the JSON.

                MISSIONS:
                """);

        for (Map<String, Object> mission : missions) {
            prompt.append("Round ")
                    .append(mission.get("round"))
                    .append(": ")
                    .append(mission.get("title"))
                    .append(" - ")
                    .append(mission.get("instruction"))
                    .append("\n");
        }

        /*
         * ---------------------------------------------------------
         * 2. Build Gemini parts
         * ---------------------------------------------------------
         *
         * IMPORTANT:
         *
         * Text and inline_data must be separate Parts.
         */

        List<Map<String, Object>> parts = new ArrayList<>();

        // First part = judging instructions.
        parts.add(
                Map.of(
                        "text",
                        prompt.toString()
                )
        );

        /*
         * Add every submitted image.
         */

        for (Map<String, Object> photo : photos) {

            String playerId =
                    String.valueOf(photo.get("playerId"));

            String round =
                    String.valueOf(photo.get("round"));

            String mimeType =
                    String.valueOf(photo.get("mimeType"));

            String base64 =
                    String.valueOf(photo.get("data"));

            /*
             * Tell Gemini which player and round this image belongs to.
             */
            parts.add(
                    Map.of(
                            "text",
                            "The following image belongs to "
                                    + "playerId=" + playerId
                                    + ", round=" + round
                                    + "."
                    )
            );

            /*
             * Image must be its own Part.
             */
            parts.add(
                    Map.of(
                            "inline_data",
                            Map.of(
                                    "mime_type",
                                    mimeType,
                                    "data",
                                    base64
                            )
                    )
            );
        }

        /*
         * ---------------------------------------------------------
         * 3. Build Gemini request body
         * ---------------------------------------------------------
         */

        Map<String, Object> generationConfig =
                Map.of(
                        "temperature",
                        0.2,

                        "responseMimeType",
                        "application/json"
                );

        Map<String, Object> body =
                Map.of(
                        "contents",
                        List.of(
                                Map.of(
                                        "role",
                                        "user",

                                        "parts",
                                        parts
                                )
                        ),

                        "generationConfig",
                        generationConfig
                );

        /*
         * ---------------------------------------------------------
         * 4. Call Gemini with automatic retry
         * ---------------------------------------------------------
         *
         * Gemini can temporarily return:
         *
         * 503 UNAVAILABLE
         * 500 INTERNAL
         * 502 BAD GATEWAY
         * 504 DEADLINE EXCEEDED
         * 429 RESOURCE EXHAUSTED
         * 408 REQUEST TIMEOUT
         *
         * These are transient errors, so retry with exponential
         * backoff instead of immediately failing the game.
         */

        String rawResponse =
                callGeminiWithRetry(body, 4);

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException(
                    "Gemini returned an empty response."
            );
        }

        /*
         * ---------------------------------------------------------
         * 5. Parse Gemini response
         * ---------------------------------------------------------
         */

        JsonNode root =
                objectMapper.readTree(rawResponse);

        JsonNode candidates =
                root.path("candidates");

        if (!candidates.isArray()
                || candidates.isEmpty()) {

            throw new IllegalStateException(
                    "Gemini returned no candidates."
            );
        }

        JsonNode content =
                candidates
                        .get(0)
                        .path("content");

        JsonNode responseParts =
                content.path("parts");

        String responseText = "";

        if (responseParts.isArray()) {

            for (JsonNode part : responseParts) {

                if (part.has("text")) {

                    responseText =
                            part.get("text")
                                    .asText();

                    break;
                }
            }
        }

        if (responseText.isBlank()) {

            throw new IllegalStateException(
                    "Gemini returned no JSON text."
            );
        }

        /*
         * Remove accidental Markdown fences.
         */
        responseText =
                stripMarkdown(responseText);

        /*
         * ---------------------------------------------------------
         * 6. Parse reviews
         * ---------------------------------------------------------
         */

        JsonNode result =
                objectMapper.readTree(
                        responseText
                );

        JsonNode reviewArray =
                result.path("reviews");

        if (!reviewArray.isArray()) {

            throw new IllegalStateException(
                    "Gemini response does not contain a reviews array."
            );
        }

        List<Map<String, Object>> reviews =
                new ArrayList<>();

        for (JsonNode review : reviewArray) {

            int round =
                    review.path("round")
                            .asInt();

            String playerId =
                    review.path("playerId")
                            .asText();

            int score =
                    review.path("score")
                            .asInt();

            /*
             * Keep score between 0 and 100.
             */
            score =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    score
                            )
                    );

            String description =
                    review.path("description")
                            .asText(
                                    "No description provided."
                            );

            String reason =
                    review.path("reason")
                            .asText(
                                    "No reason provided."
                            );

            reviews.add(
                    Map.of(
                            "round",
                            round,

                            "playerId",
                            playerId,

                            "score",
                            score,

                            "description",
                            description,

                            "reason",
                            reason
                    )
            );
        }

        return reviews;
    }

    /*
     * =============================================================
     * Gemini request with exponential backoff
     * =============================================================
     */

    private String callGeminiWithRetry(
            Map<String, Object> body,
            int maxAttempts
    ) throws InterruptedException {

        long baseDelay = 1500L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                System.out.println(
                        "Calling Gemini "
                                + model
                                + " (attempt "
                                + attempt
                                + "/"
                                + maxAttempts
                                + ")..."
                );

                String response =
                        restClient
                                .post()
                                .uri(
                                        "/v1beta/models/{model}:generateContent",
                                        model
                                )
                                .header(
                                        "x-goog-api-key",
                                        apiKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .body(body)
                                .retrieve()
                                .body(String.class);

                System.out.println(
                        "Gemini request succeeded."
                );

                return response;

            } catch (RestClientException ex) {

                String message =
                        ex.getMessage() == null
                                ? ""
                                : ex.getMessage();

                boolean retryable =
                        message.contains("408")
                                || message.contains("429")
                                || message.contains("500")
                                || message.contains("502")
                                || message.contains("503")
                                || message.contains("504")
                                || message.contains("REQUEST_TIMEOUT")
                                || message.contains("RESOURCE_EXHAUSTED")
                                || message.contains("INTERNAL")
                                || message.contains("UNAVAILABLE")
                                || message.contains("BAD_GATEWAY")
                                || message.contains("DEADLINE_EXCEEDED");

                /*
                 * Do not retry permanent errors such as:
                 *
                 * 400
                 * 401
                 * 403
                 * 404
                 *
                 * Those require fixing the request/API key/model.
                 */

                if (!retryable || attempt == maxAttempts) {

                    System.err.println(
                            "Gemini request failed permanently "
                                    + "or retry limit was reached."
                    );

                    throw ex;
                }

                /*
                 * Exponential backoff:
                 *
                 * Attempt 1 -> ~1.5s
                 * Attempt 2 -> ~3s
                 * Attempt 3 -> ~6s
                 *
                 * Add random jitter so multiple clients don't
                 * retry simultaneously.
                 */

                long exponentialDelay =
                        baseDelay * (1L << (attempt - 1));

                long jitter =
                        ThreadLocalRandom.current()
                                .nextLong(0, 1000);

                long delay =
                        Math.min(
                                exponentialDelay + jitter,
                                10000L
                        );

                System.out.println(
                        "Gemini temporarily unavailable."
                                + " Retrying in "
                                + delay
                                + " ms..."
                );

                Thread.sleep(delay);
            }
        }

        throw new IllegalStateException(
                "Gemini request failed after "
                        + maxAttempts
                        + " attempts."
        );
    }

    /*
     * =============================================================
     * Remove Markdown JSON fences
     * =============================================================
     */

    private String stripMarkdown(String text) {

        String result =
                text.trim();

        if (result.startsWith("```")) {

            int firstNewLine =
                    result.indexOf('\n');

            if (firstNewLine >= 0) {

                result =
                        result.substring(
                                firstNewLine + 1
                        );
            }

            if (result.endsWith("```")) {

                result =
                        result.substring(
                                0,
                                result.length() - 3
                        );
            }
        }

        return result.trim();
    }
}