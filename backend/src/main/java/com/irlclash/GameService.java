package com.irlclash;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Value("${game.match-radius-meters:50}")
    double radius;

    @Value("${game.presence-timeout-seconds:20}")
    long timeout;

    @Value("${game.max-image-bytes:5000000}")
    long max;

    final MissionFactory mf;
    final GeminiService gem;

    final ConcurrentHashMap<String, P> people = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, B> battles = new ConcurrentHashMap<>();

    GameService(MissionFactory mf, GeminiService gem) {
        this.mf = mf;
        this.gem = gem;
    }

    /*
     * Player presence.
     */
    record P(
            String id,
            String name,
            double lat,
            double lon,
            Instant seen
    ) {
    }

    /*
     * Submitted photo.
     */
    record Photo(
            int round,
            String playerId,
            String name,
            String type,
            byte[] bytes
    ) {
    }

    /*
     * Lightweight player information stored in a battle.
     */
    record P1(
            String id,
            String name
    ) {
    }

    /*
     * IMPORTANT:
     *
     * B must be a normal class, NOT a record.
     *
     * The battle changes during the game:
     * - round changes
     * - photos are added
     * - status changes
     * - Gemini reviews are added
     * - winner is assigned
     */
    static class B {

        String id;

        P1 a;
        P1 b;

        List<Map<String, Object>> missions;

        Map<String, Photo> photos = new ConcurrentHashMap<>();

        String status = "PLAYING";
        String message = "Battle started!";

        String winnerId = "";
        String winner = "";

        int round = 1;

        long started = System.currentTimeMillis();

        int timedOut = 0;

        List<Map<String, Object>> reviews = List.of();
        List<Map<String, Object>> results = List.of();

        B(
                String id,
                P1 a,
                P1 b,
                List<Map<String, Object>> missions
        ) {
            this.id = id;
            this.a = a;
            this.b = b;
            this.missions = missions;
        }
    }

    /*
     * Register a new player.
     */
    public GameModels.Register register(String name) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nickname is required.");
        }

        String n = name.trim();

        if (n.length() > 24) {
            n = n.substring(0, 24);
        }

        return new GameModels.Register(
                UUID.randomUUID().toString(),
                n
        );
    }

    /*
     * Update player location and automatically search
     * for another nearby player.
     */
    public GameModels.Match locate(GameModels.Location x) {

        valid(x.latitude(), x.longitude());

        people.put(
                x.playerId(),
                new P(
                        x.playerId(),
                        x.username(),
                        x.latitude(),
                        x.longitude(),
                        Instant.now()
                )
        );

        clean();

        /*
         * Check whether this player is already in a battle.
         */
        B active = active(x.playerId());

        if (active != null) {

            P1 opponent = active.a.id.equals(x.playerId())
                    ? active.b
                    : active.a;

            return match(
                    true,
                    active.id,
                    new GameModels.Near(
                            opponent.id,
                            opponent.name,
                            distOf(active, x.playerId())
                    ),
                    "Already in battle."
            );
        }

        P me = people.get(x.playerId());

        if (me == null) {
            throw new IllegalArgumentException("Player presence not found.");
        }

        /*
         * Search for another nearby active player.
         */
        for (P q : people.values()) {

            if (q.id.equals(me.id)) {
                continue;
            }

            if (active(q.id) != null) {
                continue;
            }

            if (Duration.between(q.seen, Instant.now()).getSeconds() > timeout) {
                continue;
            }

            double d = dist(
                    me.lat,
                    me.lon,
                    q.lat,
                    q.lon
            );

            if (d <= radius) {

                B b = new B(
                        UUID.randomUUID().toString(),
                        new P1(me.id, me.name),
                        new P1(q.id, q.name),
                        mf.pick()
                );

                battles.put(b.id, b);

                return match(
                        true,
                        b.id,
                        new GameModels.Near(
                                q.id,
                                q.name,
                                Math.round(d)
                        ),
                        "Opponent found. Battle starting!"
                );
            }
        }

        return match(
                false,
                "",
                null,
                "Looking for a nearby player..."
        );
    }

    /*
     * Create Match response.
     */
    GameModels.Match match(
            boolean found,
            String id,
            GameModels.Near near,
            String message
    ) {
        return new GameModels.Match(
                found,
                id,
                near,
                message
        );
    }

    /*
     * Get current battle state.
     */
    public Map<String, Object> state(
            String id,
            String pid
    ) {

        B b = get(id);

        synchronized (b) {

            timeout(b);

            P1 me;
            P1 opponent;

            /*
             * Make sure the requested player actually belongs
             * to this battle.
             */
            if (b.a.id.equals(pid)) {

                me = b.a;
                opponent = b.b;

            } else if (b.b.id.equals(pid)) {

                me = b.b;
                opponent = b.a;

            } else {

                throw new IllegalArgumentException(
                        "Player is not in this battle."
                );
            }

            Map<String, Object> r = new LinkedHashMap<>();

            r.put("battleId", b.id);
            r.put("status", b.status);
            r.put("round", b.round);
            r.put("totalRounds", 5);

            /*
             * Mission only exists while playing.
             */
            if ("PLAYING".equals(b.status)
                    && b.round >= 1
                    && b.round <= b.missions.size()) {

                r.put(
                        "mission",
                        b.missions.get(b.round - 1)
                );

            } else {

                r.put("mission", null);
            }

            r.put(
                    "me",
                    Map.of(
                            "playerId", me.id,
                            "username", me.name
                    )
            );

            r.put(
                    "opponent",
                    Map.of(
                            "playerId", opponent.id,
                            "username", opponent.name,
                            "distanceMeters",
                            Math.round(
                                    distanceBetween(
                                            me.id,
                                            opponent.id
                                    )
                            )
                    )
            );

            /*
             * Photo submission state.
             */
            if (b.round >= 1 && b.round <= 5) {

                r.put(
                        "myPhotoSubmitted",
                        b.photos.containsKey(
                                k(me.id, b.round)
                        )
                );

                r.put(
                        "opponentPhotoSubmitted",
                        b.photos.containsKey(
                                k(opponent.id, b.round)
                        )
                );

            } else {

                r.put("myPhotoSubmitted", false);
                r.put("opponentPhotoSubmitted", false);
            }

            /*
             * Remaining time.
             */
            long left = 0;

            if ("PLAYING".equals(b.status)
                    && b.round >= 1
                    && b.round <= b.missions.size()) {

                long seconds =
                        ((Number) b.missions
                                .get(b.round - 1)
                                .get("seconds"))
                                .longValue();

                long elapsed =
                        (System.currentTimeMillis() - b.started) / 1000;

                left = Math.max(
                        0,
                        seconds - elapsed
                );
            }

            r.put("remainingSeconds", left);
            r.put("message", b.message);

            /*
             * Gemini image reviews.
             */
            r.put("results", b.reviews);

            /*
             * Final player totals.
             */
            r.put("playerResults", b.results);

            r.put("winnerPlayerId", b.winnerId);
            r.put("winnerUsername", b.winner);

            return r;
        }
    }

    /*
     * Receive a player's photo.
     */
    public Map<String, Object> photo(
            String id,
            String pid,
            MultipartFile f
    ) throws Exception {

        B b = get(id);

        synchronized (b) {

            timeout(b);

            if (!"PLAYING".equals(b.status)) {
                throw new IllegalStateException(
                        "Battle is not accepting photos."
                );
            }

            /*
             * Validate that this player actually belongs
             * to the battle.
             */
            P1 p;

            if (b.a.id.equals(pid)) {

                p = b.a;

            } else if (b.b.id.equals(pid)) {

                p = b.b;

            } else {

                throw new IllegalArgumentException(
                        "Player is not in this battle."
                );
            }

            if (f == null || f.isEmpty()) {
                throw new IllegalArgumentException(
                        "Select an image."
                );
            }

            if (f.getSize() > max) {
                throw new IllegalArgumentException(
                        "Image must be 5 MB or smaller."
                );
            }

            if (!allowed(f.getContentType())) {
                throw new IllegalArgumentException(
                        "Use JPEG, PNG or WebP."
                );
            }

            String key = k(
                    pid,
                    b.round
            );

            if (b.photos.containsKey(key)) {
                throw new IllegalStateException(
                        "Already submitted this round."
                );
            }

            /*
             * Store image bytes in memory.
             */
            b.photos.put(
                    key,
                    new Photo(
                            b.round,
                            p.id,
                            p.name,
                            f.getContentType(),
                            f.getBytes()
                    )
            );

            /*
             * If both players have submitted,
             * move to next round.
             */
            if (done(b)) {

                if (b.round == 5) {

                    b.status = "EVALUATING";
                    b.message =
                            "All five rounds complete. Gemini is judging.";

                    judge(b);

                } else {

                    b.round++;
                    b.started =
                            System.currentTimeMillis();
                    b.timedOut = 0;
                    b.message = "New mission!";
                }
            }

            return Map.of(
                    "accepted",
                    true,
                    "message",
                    "Photo submitted successfully."
            );
        }
    }

    /*
     * Check whether the current round has timed out.
     *
     * This method is called whenever the frontend asks
     * for the latest state or uploads a photo.
     */
    void timeout(B b) {

        if (!"PLAYING".equals(b.status)) {
            return;
        }

        if (b.round < 1 || b.round > b.missions.size()) {
            return;
        }

        long seconds =
                ((Number) b.missions
                        .get(b.round - 1)
                        .get("seconds"))
                        .longValue();

        long elapsed =
                (System.currentTimeMillis() - b.started) / 1000;

        if (elapsed > seconds) {

            b.timedOut = b.round;

            /*
             * The round is considered complete when the timer
             * expires. Missing photos simply receive no score.
             */
            if (done(b)) {

                if (b.round == 5) {

                    b.status = "EVALUATING";
                    b.message =
                            "Time is up. Gemini is judging your photos.";

                    judge(b);

                } else {

                    b.round++;
                    b.started =
                            System.currentTimeMillis();

                    b.timedOut = 0;

                    b.message =
                            "Time is up! New mission!";
                }
            }
        }
    }

    /*
     * Check whether both players have completed the
     * current round.
     *
     * A player is considered complete if:
     * - they submitted a photo
     * OR
     * - the round timed out.
     */
    boolean done(B b) {

        boolean playerAComplete =
                b.photos.containsKey(
                        k(b.a.id, b.round)
                )
                        || b.timedOut == b.round;

        boolean playerBComplete =
                b.photos.containsKey(
                        k(b.b.id, b.round)
                )
                        || b.timedOut == b.round;

        return playerAComplete && playerBComplete;
    }

    /*
     * Send all submitted photos to Gemini for judging.
     */
    void judge(B b) {

        try {

            List<Map<String, Object>> ps =
                    new ArrayList<>();

            for (Photo p : b.photos.values()) {

                ps.add(
                        Map.of(
                                "round",
                                p.round,

                                "playerId",
                                p.playerId,

                                "mimeType",
                                p.type,

                                "data",
                                Base64.getEncoder()
                                        .encodeToString(
                                                p.bytes
                                        )
                        )
                );
            }

            /*
             * Gemini evaluates every submitted image.
             */
            List<Map<String, Object>> raw =
                    gem.judge(
                            b.missions,
                            ps
                    );

            Map<String, String> names =
                    Map.of(
                            b.a.id,
                            b.a.name,

                            b.b.id,
                            b.b.name
                    );

            /*
             * Build set of photos that actually exist.
             */
            Set<String> actual =
                    b.photos.values()
                            .stream()
                            .map(
                                    x -> k(
                                            x.playerId,
                                            x.round
                                    )
                            )
                            .collect(Collectors.toSet());

            /*
             * Keep only reviews that correspond to
             * actual submitted photos.
             */
            b.reviews =
                    raw.stream()
                            .filter(x -> {

                                Object player =
                                        x.get("playerId");

                                Object round =
                                        x.get("round");

                                if (!(player instanceof String)
                                        || !(round instanceof Number)) {

                                    return false;
                                }

                                return actual.contains(
                                        k(
                                                (String) player,
                                                ((Number) round)
                                                        .intValue()
                                        )
                                );
                            })
                            .map(x -> {

                                Map<String, Object> y =
                                        new LinkedHashMap<>(x);

                                y.put(
                                        "username",
                                        names.getOrDefault(
                                                x.get("playerId"),
                                                "Player"
                                        )
                                );

                                return y;
                            })
                            .toList();

            /*
             * Calculate final totals.
             */
            b.results =
                    List.of(
                            result(
                                    b.a,
                                    b.reviews
                            ),
                            result(
                                    b.b,
                                    b.reviews
                            )
                    );

            Map<String, Object> one =
                    b.results.get(0);

            Map<String, Object> two =
                    b.results.get(1);

            int scoreOne =
                    ((Number) one.get("totalScore"))
                            .intValue();

            int scoreTwo =
                    ((Number) two.get("totalScore"))
                            .intValue();

            /*
             * Player A wins ties.
             */
            Map<String, Object> win =
                    scoreOne >= scoreTwo
                            ? one
                            : two;

            b.winnerId =
                    (String) win.get("playerId");

            b.winner =
                    (String) win.get("username");

            b.message =
                    b.winner
                            + " wins the IRL Clash!";

            b.status = "FINISHED";

        } catch (Exception e) {

            b.status = "ERROR";

            b.message =
                    "AI judging failed: "
                            + e.getMessage();
        }
    }

    /*
     * Calculate one player's total score.
     */
    Map<String, Object> result(
            P1 p,
            List<Map<String, Object>> reviews
    ) {

        List<Map<String, Object>> own =
                reviews.stream()
                        .filter(
                                x -> p.id.equals(
                                        x.get("playerId")
                                )
                        )
                        .sorted(
                                Comparator.comparingInt(
                                        x -> ((Number)
                                                x.get("round"))
                                                .intValue()
                                )
                        )
                        .toList();

        int total =
                own.stream()
                        .mapToInt(
                                x -> ((Number)
                                        x.get("score"))
                                        .intValue()
                        )
                        .sum();

        return Map.of(
                "playerId",
                p.id,

                "username",
                p.name,

                "totalScore",
                total,

                "images",
                own
        );
    }

    /*
     * Find a battle by ID.
     */
    B get(String id) {

        B b = battles.get(id);

        if (b == null) {
            throw new IllegalArgumentException(
                    "Battle not found."
            );
        }

        return b;
    }

    /*
     * Find an active battle containing this player.
     */
    B active(String pid) {

        return battles.values()
                .stream()
                .filter(
                        x ->
                                !"ERROR".equals(x.status)
                                        && !"FINISHED".equals(x.status)
                                        && (
                                        x.a.id.equals(pid)
                                                || x.b.id.equals(pid)
                                )
                )
                .findFirst()
                .orElse(null);
    }

    /*
     * Remove players whose location updates have expired.
     */
    void clean() {

        Instant cutoff =
                Instant.now()
                        .minusSeconds(timeout);

        people.values().removeIf(
                x -> x.seen.isBefore(cutoff)
        );
    }

    /*
     * Calculate distance between two currently active players.
     */
    double distanceBetween(
            String a,
            String z
    ) {

        P x = people.get(a);
        P y = people.get(z);

        if (x == null || y == null) {
            return 0;
        }

        return dist(
                x.lat,
                x.lon,
                y.lat,
                y.lon
        );
    }

    /*
     * Haversine distance calculation.
     *
     * Returns meters.
     */
    double dist(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double R = 6_371_000;

        double dLat =
                Math.toRadians(lat2 - lat1);

        double dLon =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2)
                        * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);

        return R
                * 2
                * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );
    }

    /*
     * Validate latitude and longitude.
     */
    void valid(
            double latitude,
            double longitude
    ) {

        if (!Double.isFinite(latitude)
                || latitude < -90
                || latitude > 90) {

            throw new IllegalArgumentException(
                    "Invalid latitude."
            );
        }

        if (!Double.isFinite(longitude)
                || longitude < -180
                || longitude > 180) {

            throw new IllegalArgumentException(
                    "Invalid longitude."
            );
        }
    }

    /*
     * Validate supported image formats.
     */
    boolean allowed(String type) {

        return "image/jpeg".equalsIgnoreCase(type)
                || "image/png".equalsIgnoreCase(type)
                || "image/webp".equalsIgnoreCase(type);
    }

    /*
     * Generate a unique key for:
     * player + round
     */
    String k(
            String playerId,
            int round
    ) {

        return playerId + ":" + round;
    }

    /*
     * Get the distance between the two players
     * currently stored in a battle.
     */
    long distOf(
            B b,
            String id
    ) {

        String firstId;
        String secondId;

        if (b.a.id.equals(id)) {

            firstId = b.a.id;
            secondId = b.b.id;

        } else if (b.b.id.equals(id)) {

            firstId = b.b.id;
            secondId = b.a.id;

        } else {

            return 0;
        }

        return Math.round(
                distanceBetween(
                        firstId,
                        secondId
                )
        );
    }
}