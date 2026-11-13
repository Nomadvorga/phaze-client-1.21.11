package vorga.phazeclient.implement.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.base.util.RemoteRulesService;
import vorga.phazeclient.base.util.PhazeEventService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CosmeticsSyncService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeCosmeticsSync");
    private static final CosmeticsSyncService INSTANCE = new CosmeticsSyncService();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_QUERY_PLAYERS = 200;
    private static final long FALLBACK_REFRESH_MS = 10L * 60L * 1000L;
    private static final int MAX_CACHED_PLAYERS = 4096;
    private static final long ROSTER_CHECK_MS = 500L;
    private static final long BATCH_WINDOW_MS = 400L;
    private static final long MIN_QUERY_INTERVAL_MS = 5_000L;
    private static final long FAILURE_BACKOFF_MIN_MS = 5_000L;
    private static final long FAILURE_BACKOFF_MAX_MS = 120_000L;
    private static final long RATE_LIMITED_BACKOFF_MS = 65_000L;
    private static final String SLOT_SEPARATOR = "|";

    private static final String LEGACY_SOURCE = "pu" + "lse";
    private static final String LEGACY_REVISIONS_FIELD = LEGACY_SOURCE + "Revisions";

    private final ScheduledExecutorService io =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "phaze-cosmetics-sync");
                thread.setDaemon(true);
                return thread;
            });
    private final Map<UUID, RemoteCosmetic> states = new ConcurrentHashMap<>();
    private final Map<UUID, PhazeRemoteCosmetic> phazeStates = new ConcurrentHashMap<>();
    // Each source is confirmed separately: a push from one must not hide the other.
    private final Map<UUID, Long> confirmedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> phazeConfirmedAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> publishedPayloads = new ConcurrentHashMap<>();
    private final Set<UUID> cachedPlayers = new HashSet<>();
    private final AtomicInteger cacheEpoch = new AtomicInteger();
    private final Map<String, PhazeGraffiti> phazeGraffiti = new ConcurrentHashMap<>();
    private final AtomicBoolean queryInFlight = new AtomicBoolean(false);
    private final AtomicBoolean graffitiQueryInFlight = new AtomicBoolean(false);
    private final AtomicReference<GraffitiQuery> pendingGraffitiQuery = new AtomicReference<>();

    private ScheduledFuture<?> pendingPublish;
    private UUID localPlayerUuid;
    private long lastRosterCheckMs;
    private long pendingSinceMs;
    private volatile long nextQueryAllowedMs;
    private volatile long failureBackoffMs = FAILURE_BACKOFF_MIN_MS;
    private volatile long nextPublishAllowedMs;
    private volatile boolean publishOnJoin = true;
    private volatile String graffitiContext = "";
    private volatile long lastGraffitiQueryMs;
    private volatile long nextGraffitiQueryAllowedMs;
    private volatile long lastPhazeCosmeticRevision;
    private volatile long lastPhazeGraffitiRevision;

    private CosmeticsSyncService() {
    }

    public static CosmeticsSyncService getInstance() {
        return INSTANCE;
    }

    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null
                || client.getNetworkHandler() == null) {
            localPlayerUuid = null;
            pendingSinceMs = 0L;
            // Cosmetic presence is global; only world-local graffiti is discarded.
            phazeGraffiti.clear();
            pendingGraffitiQuery.set(null);
            graffitiContext = "";
            publishOnJoin = true;
            return;
        }

        UUID currentUuid = client.player.getUuid();
        if (!currentUuid.equals(localPlayerUuid)) {
            localPlayerUuid = currentUuid;
            publishOnJoin = true;
        }
        long now = System.currentTimeMillis();
        if (publishOnJoin && now >= nextPublishAllowedMs) {
            publishOnJoin = false;
            publishLocalState();
        }

        if (now - lastRosterCheckMs < ROSTER_CHECK_MS) return;
        lastRosterCheckMs = now;

        List<UUID> players = loadedPlayerUuids(client);
        trimCache(players);
        List<UUID> unknown = unconfirmed(players, now);
        if (unknown.isEmpty()) {
            pendingSinceMs = 0L;
        } else {
            if (pendingSinceMs == 0L) pendingSinceMs = now;
            if (now - pendingSinceMs >= BATCH_WINDOW_MS && now >= nextQueryAllowedMs
                    && query(unknown)) pendingSinceMs = 0L;
        }

        String serverKey = vorga.phazeclient.implement.cosmetics.bridge.SharedWorldKey.get(client);
        String dimension = vorga.phazeclient.implement.cosmetics.bridge.SharedWorldKey.dimension(client);
        String context = String.valueOf(serverKey) + '|' + dimension;
        if (!context.equals(graffitiContext)) {
            phazeGraffiti.clear();
            graffitiContext = context;
            lastGraffitiQueryMs = 0L;
        }
        if (serverKey != null && dimension != null && now >= nextGraffitiQueryAllowedMs
                && (!context.equals(graffitiContext)
                || now - lastGraffitiQueryMs >= FALLBACK_REFRESH_MS)) {
            graffitiContext = context;
            queryGraffiti(serverKey, dimension);
        }
    }

    public synchronized void publishLocalState() {
        if (pendingPublish != null) pendingPublish.cancel(false);
        pendingPublish = io.schedule(this::postLocalState, 120L, TimeUnit.MILLISECONDS);
    }

    public void requestRefresh() {
        invalidatePresence();
        lastGraffitiQueryMs = 0L;
    }

    private void invalidatePresence() {
        cacheEpoch.incrementAndGet();
        confirmedAt.clear();
        phazeConfirmedAt.clear();
        // A stream reconnect must not bypass an active HTTP rate-limit backoff.
    }

    public void acceptEvent(JsonObject event) {
        try {
            UUID uuid = UUID.fromString(event.get("playerUuid").getAsString());
            String cosmetic = event.get("cosmetic").getAsString();
            boolean equipped = event.get("equipped").getAsBoolean();
            long updatedAt = event.get("updatedAt").getAsLong();
            RemoteCosmetic decoded = decode(cosmetic, equipped, updatedAt);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            client.execute(() -> {
                putIfNewer(uuid, decoded);
                confirmedAt.put(uuid, System.currentTimeMillis());
                trimCache(List.of());
            });
        } catch (Throwable error) {
            LOG.debug("invalid cosmetic event: {}", error.toString());
        }
    }

    public void acceptPhazeEvent(JsonObject event) {
        try {
            notePhazeRevision(event, false);
            UUID uuid = UUID.fromString(event.get("playerUuid").getAsString());
            PhazeRemoteCosmetic decoded = decodePhaze(
                    event.get("cosmetic").getAsString(),
                    event.get("equipped").getAsBoolean(),
                    event.get("updatedAt").getAsLong()
            );
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            client.execute(() -> {
                putPhazeIfNewer(uuid, decoded);
                phazeConfirmedAt.put(uuid, System.currentTimeMillis());
                trimCache(List.of());
            });
        } catch (Throwable error) {
            LOG.debug("invalid phaze cosmetic event: {}", error.toString());
        }
    }

    public void acceptPhazeGraffitiEvent(JsonObject event) {
        notePhazeRevision(event, true);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                String serverKey = event.get("serverKey").getAsString();
                String dimension = event.get("dimension").getAsString();
                if (!(serverKey + '|' + dimension).equals(graffitiContext)) return;
                BlockPos pos = new BlockPos(event.get("x").getAsInt(), event.get("y").getAsInt(), event.get("z").getAsInt());
                Direction face = Direction.byId(event.get("face").getAsString());
                if (face == null) return;
                String key = graffitiKey(serverKey, dimension, pos, face);
                if ("delete".equals(event.get("action").getAsString())) {
                    phazeGraffiti.remove(key);
                } else {
                    int id = event.get("graffitiId").getAsInt();
                    vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry entry = vorga.phazeclient.implement.cosmetics.bridge.CosmeticCatalog.byId(id);
                    if (entry != null && entry.category() == vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory.GRAFFITI) {
                        phazeGraffiti.put(key, new PhazeGraffiti(serverKey, dimension, pos, face, id));
                    }
                }
                if (graffitiQueryInFlight.get()) queryGraffiti(serverKey, dimension);
            } catch (Throwable error) {
                LOG.debug("invalid shared graffiti event: {}", error.toString());
            }
        });
    }

    public List<PhazeGraffiti> phazeGraffiti() {
        return List.copyOf(phazeGraffiti.values());
    }

    public void acceptPushCheckpoint(JsonObject payload) {
        try {
            JsonObject revisions = payload.getAsJsonObject(LEGACY_REVISIONS_FIELD);
            if (revisions == null) return;
            long cosmetic = revisions.get("cosmetic").getAsLong();
            long graffiti = revisions.get("graffiti").getAsLong();
            if (cosmetic > lastPhazeCosmeticRevision) {
                lastPhazeCosmeticRevision = cosmetic;
                invalidatePresence();
            }
            if (graffiti > lastPhazeGraffitiRevision) {
                lastPhazeGraffitiRevision = graffiti;
                lastGraffitiQueryMs = 0L;
            }
        } catch (Throwable error) {
            LOG.debug("invalid push checkpoint: {}", error.toString());
        }
    }

    private void notePhazeRevision(JsonObject event, boolean graffiti) {
        if (!event.has("revision")) return;
        long revision = event.get("revision").getAsLong();
        if (graffiti) lastPhazeGraffitiRevision = Math.max(lastPhazeGraffitiRevision, revision);
        else lastPhazeCosmeticRevision = Math.max(lastPhazeCosmeticRevision, revision);
    }

    public vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry phazeEntryFor(
            UUID playerUuid,
            vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory category
    ) {
        if (playerUuid == null) return null;
        PhazeRemoteCosmetic state = phazeStates.get(playerUuid);
        if (state == null || !state.equipped) return null;
        int id = state.id(category);
        vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry entry = vorga.phazeclient.implement.cosmetics.bridge.CosmeticCatalog.byId(id);
        return entry != null && entry.category() == category ? entry : null;
    }

    public String selectionFor(UUID playerUuid) {
        String wing = wingSelectionFor(playerUuid);
        return wing != null ? wing : capeSelectionFor(playerUuid);
    }

    public String wingSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.wing)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.wing)
                ? state.wing
                : null;
    }

    public String capeSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.cape)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.cape)
                ? state.cape
                : null;
    }

    public String hatSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.hat)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.hat)
                ? state.hat
                : null;
    }

    public String petSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.pet)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.pet)
                ? state.pet
                : null;
    }

    private void postLocalState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> prepareLocalState(client));
    }

    private void prepareLocalState(MinecraftClient client) {
        if (client == null || client.player == null) {
            publishOnJoin = true;
            return;
        }
        RemoteRulesService rules = RemoteRulesService.getInstance();
        if (rules.getApiBase() == null || rules.getApiBase().isBlank()) {
            nextPublishAllowedMs = System.currentTimeMillis() + FAILURE_BACKOFF_MIN_MS;
            publishOnJoin = true;
            return;
        }

        CosmeticsState local = CosmeticsState.getInstance();
        JsonObject body = new JsonObject();
        body.addProperty("clientId", rules.getClientId());
        String wing = local.isWingEquipped() ? local.getSelectedWing() : CosmeticsState.NONE;
        String cape = local.isCapeEquipped() ? local.getSelectedCape() : CosmeticsState.NONE;
        String hat = local.isHatEquipped() ? local.getSelectedHat() : CosmeticsState.NONE;
        String pet = local.isPetEquipped() ? local.getSelectedPet() : CosmeticsState.NONE;
        String cosmetic = String.join(SLOT_SEPARATOR, wing, cape, hat, pet);
        boolean equipped = local.isEquipped();
        UUID playerUuid = client.player.getUuid();
        String payloadKey = rules.getApiBase() + '#' + rules.getClientId() + '#' + cosmetic + '#' + equipped;
        if (payloadKey.equals(publishedPayloads.get(playerUuid))) return;
        body.addProperty("playerUuid", playerUuid.toString());
        body.addProperty("cosmetic", cosmetic);
        body.addProperty("equipped", equipped);
        io.execute(() -> sendLocalState(playerUuid, payloadKey, body));
    }

    private void sendLocalState(UUID playerUuid, String payloadKey, JsonObject body) {
        if (payloadKey.equals(publishedPayloads.get(playerUuid))) return;
        long now = System.currentTimeMillis();
        if (now < nextPublishAllowedMs) {
            publishOnJoin = true;
            return;
        }
        try {
            request("/api/cosmetics/state", body);
            if (publishedPayloads.size() >= 64 && !publishedPayloads.containsKey(playerUuid)) publishedPayloads.clear();
            publishedPayloads.put(playerUuid, payloadKey);
        } catch (HttpStatusException error) {
            LOG.debug("state publish rejected: HTTP {}", error.status);
            if (error.status == 429 || error.status >= 500) {
                nextPublishAllowedMs = now + (error.status == 429 ? RATE_LIMITED_BACKOFF_MS : FAILURE_BACKOFF_MIN_MS);
                publishOnJoin = true;
            }
        } catch (Throwable error) {
            LOG.debug("state publish failed: {}", error.toString());
            nextPublishAllowedMs = System.currentTimeMillis() + FAILURE_BACKOFF_MIN_MS;
            publishOnJoin = true;
        }
    }

    private boolean query(List<UUID> playerUuids) {
        if (playerUuids.isEmpty() || !queryInFlight.compareAndSet(false, true)) return false;
        String apiBase = RemoteRulesService.getInstance().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            queryInFlight.set(false);
            return false;
        }
        List<UUID> requested = List.copyOf(playerUuids.size() > MAX_QUERY_PLAYERS
                ? playerUuids.subList(0, MAX_QUERY_PLAYERS) : playerUuids);
        long startedAt = System.currentTimeMillis();
        int epoch = cacheEpoch.get();
        nextQueryAllowedMs = startedAt + MIN_QUERY_INTERVAL_MS;
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray ids = new JsonArray();
                requested.forEach(uuid -> ids.add(uuid.toString()));
                body.add("playerUuids", ids);
                JsonArray sources = new JsonArray();
                sources.add("phaze");
                sources.add(LEGACY_SOURCE);
                body.add("sources", sources);
                JsonObject response = request("/api/cosmetics/query", body);
                JsonArray array = response.getAsJsonArray("states");
                Set<UUID> requestedSet = new HashSet<>(requested);
                Map<UUID, RemoteCosmetic> returned = new HashMap<>();
                Map<UUID, PhazeRemoteCosmetic> phazeReturned = new HashMap<>();
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) continue;
                        JsonObject value = element.getAsJsonObject();
                        UUID uuid = UUID.fromString(value.get("playerUuid").getAsString());
                        if (!requestedSet.contains(uuid)) continue;
                        String source = value.has("source") ? value.get("source").getAsString() : "phaze";
                        if (LEGACY_SOURCE.equals(source)) {
                            phazeReturned.put(uuid, decodePhaze(
                                    value.get("cosmetic").getAsString(),
                                    value.get("equipped").getAsBoolean(),
                                    value.get("updatedAt").getAsLong()
                            ));
                        } else if ("phaze".equals(source)) {
                            returned.put(uuid, decode(
                                    value.get("cosmetic").getAsString(),
                                    value.get("equipped").getAsBoolean(),
                                    value.get("updatedAt").getAsLong()
                            ));
                        }
                    }
                }
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) client.execute(() -> {
                    if (cacheEpoch.get() != epoch) return;
                    returned.forEach(this::putIfNewer);
                    phazeReturned.forEach(this::putPhazeIfNewer);
                    long confirmed = System.currentTimeMillis();
                    for (UUID uuid : requested) {
                        // Do not remove a push received after this query began.
                        if (!returned.containsKey(uuid)) states.computeIfPresent(uuid, (ignored, state) ->
                                state.receivedAt < startedAt ? null : state);
                        if (!phazeReturned.containsKey(uuid)) phazeStates.computeIfPresent(uuid, (ignored, state) ->
                                state.receivedAt < startedAt ? null : state);
                        confirmedAt.put(uuid, confirmed);
                        phazeConfirmedAt.put(uuid, confirmed);
                        cachedPlayers.add(uuid);
                    }
                    trimCache(requested);
                });
                failureBackoffMs = FAILURE_BACKOFF_MIN_MS;
            } catch (HttpStatusException error) {
                LOG.debug("roster query rejected: HTTP {}", error.status);
                if (error.status == 429) nextQueryAllowedMs = System.currentTimeMillis() + RATE_LIMITED_BACKOFF_MS;
                else if (error.status >= 400 && error.status < 500) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) client.execute(() -> {
                        if (cacheEpoch.get() != epoch) return;
                        long confirmed = System.currentTimeMillis();
                        requested.forEach(uuid -> {
                            confirmedAt.put(uuid, confirmed);
                            phazeConfirmedAt.put(uuid, confirmed);
                            cachedPlayers.add(uuid);
                        });
                        trimCache(requested);
                    });
                    nextQueryAllowedMs = System.currentTimeMillis() + FAILURE_BACKOFF_MAX_MS;
                } else backOff();
            } catch (Throwable error) {
                LOG.debug("roster query failed: {}", error.toString());
                backOff();
            } finally {
                queryInFlight.set(false);
            }
        });
        return true;
    }

    private void backOff() {
        long delay = failureBackoffMs;
        failureBackoffMs = Math.min(FAILURE_BACKOFF_MAX_MS, delay * 2L);
        nextQueryAllowedMs = System.currentTimeMillis() + delay;
    }

    private void queryGraffiti(String serverKey, String dimension) {
        if (System.currentTimeMillis() < nextGraffitiQueryAllowedMs) {
            lastGraffitiQueryMs = 0L;
            return;
        }
        pendingGraffitiQuery.set(new GraffitiQuery(serverKey, dimension, cacheEpoch.get()));
        startGraffitiQueryDrain();
    }

    private void startGraffitiQueryDrain() {
        if (!graffitiQueryInFlight.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                GraffitiQuery query;
                while ((query = pendingGraffitiQuery.getAndSet(null)) != null) {
                    try {
                        GraffitiQuery currentQuery = query;
                        lastGraffitiQueryMs = System.currentTimeMillis();
                        JsonObject body = new JsonObject();
                        body.addProperty("serverKey", currentQuery.serverKey());
                        body.addProperty("dimension", currentQuery.dimension());
                        JsonArray array = request("/api/graffiti/query", body).getAsJsonArray("graffiti");
                        Map<String, PhazeGraffiti> next = new ConcurrentHashMap<>();
                        if (array != null) for (JsonElement element : array) {
                            JsonObject value = element.getAsJsonObject();
                            int id = value.get("graffitiId").getAsInt();
                            vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry entry = vorga.phazeclient.implement.cosmetics.bridge.CosmeticCatalog.byId(id);
                            Direction face = Direction.byId(value.get("face").getAsString());
                            if (entry == null || entry.category() != vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory.GRAFFITI || face == null) continue;
                            BlockPos pos = new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
                            PhazeGraffiti graffiti = new PhazeGraffiti(currentQuery.serverKey(), currentQuery.dimension(), pos, face, id);
                            next.put(graffitiKey(currentQuery.serverKey(), currentQuery.dimension(), pos, face), graffiti);
                        }
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) client.execute(() -> {
                            if (cacheEpoch.get() != currentQuery.epoch()
                                    || !(currentQuery.serverKey() + '|' + currentQuery.dimension()).equals(graffitiContext)) return;
                            phazeGraffiti.entrySet().removeIf(entry ->
                                    entry.getValue().serverKey.equals(currentQuery.serverKey())
                                            && entry.getValue().dimension.equals(currentQuery.dimension()));
                            phazeGraffiti.putAll(next);
                        });
                        lastGraffitiQueryMs = System.currentTimeMillis();
                    } catch (HttpStatusException error) {
                        nextGraffitiQueryAllowedMs = System.currentTimeMillis()
                                + (error.status == 429 ? RATE_LIMITED_BACKOFF_MS : FAILURE_BACKOFF_MIN_MS);
                        lastGraffitiQueryMs = 0L;
                        pendingGraffitiQuery.set(null);
                        LOG.debug("shared graffiti query rejected: HTTP {}", error.status);
                        break;
                    } catch (Throwable error) {
                        nextGraffitiQueryAllowedMs = System.currentTimeMillis() + FAILURE_BACKOFF_MIN_MS;
                        lastGraffitiQueryMs = 0L;
                        pendingGraffitiQuery.set(null);
                        LOG.debug("shared graffiti query failed: {}", error.toString());
                        break;
                    }
                }
            } finally {
                graffitiQueryInFlight.set(false);
                if (pendingGraffitiQuery.get() != null) startGraffitiQueryDrain();
            }
        });
    }

    private static String graffitiKey(String serverKey, String dimension, BlockPos pos, Direction face) {
        return serverKey + '|' + dimension + '|' + pos.getX() + '|' + pos.getY()
                + '|' + pos.getZ() + '|' + face.asString();
    }

    private JsonObject request(String path, JsonObject body) throws Exception {
        String base = RemoteRulesService.getInstance().getApiBase();
        HttpURLConnection connection = (HttpURLConnection)
                URI.create(base + path).toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/126.0.0.0 Safari/537.36");
            connection.setDoOutput(true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    json.append(buffer, 0, count);
                }
                if (json.isEmpty()) return new JsonObject();
                JsonElement parsed = JsonParser.parseString(json.toString());
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            }
        } finally {
            connection.disconnect();
        }
    }

    private List<UUID> loadedPlayerUuids(MinecraftClient client) {
        List<UUID> result = new ArrayList<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            UUID uuid = player.getUuid();
            if (uuid.version() == 2) continue;
            result.add(uuid);
        }
        result.sort(Comparator.comparing(UUID::toString));
        return result;
    }

    private List<UUID> unconfirmed(List<UUID> loaded, long now) {
        boolean live = PhazeEventService.getInstance().isConnected();
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : loaded) {
            Long confirmed = confirmedAt.get(uuid);
            Long phazeConfirmed = phazeConfirmedAt.get(uuid);
            if (confirmed == null || phazeConfirmed == null || (!live
                    && (now - confirmed >= FALLBACK_REFRESH_MS || now - phazeConfirmed >= FALLBACK_REFRESH_MS))) {
                result.add(uuid);
            }
        }
        return result;
    }

    private void trimCache(List<UUID> loaded) {
        if (cachedPlayers.size() <= MAX_CACHED_PLAYERS) return;
        Set<UUID> keep = new HashSet<>(loaded);
        List<UUID> oldest = new ArrayList<>(cachedPlayers);
        oldest.sort(Comparator.comparingLong(uuid -> Math.max(
                confirmedAt.getOrDefault(uuid, 0L), phazeConfirmedAt.getOrDefault(uuid, 0L))));
        int remaining = cachedPlayers.size();
        for (UUID uuid : oldest) {
            if (remaining <= MAX_CACHED_PLAYERS) break;
            if (keep.size() <= MAX_CACHED_PLAYERS && keep.contains(uuid)) continue;
            confirmedAt.remove(uuid);
            phazeConfirmedAt.remove(uuid);
            states.remove(uuid);
            phazeStates.remove(uuid);
            cachedPlayers.remove(uuid);
            remaining--;
        }
    }

    private void putIfNewer(UUID uuid, RemoteCosmetic next) {
        cachedPlayers.add(uuid);
        states.compute(uuid, (ignored, current) ->
                current == null || next.updatedAt >= current.updatedAt ? next : current);
    }

    private void putPhazeIfNewer(UUID uuid, PhazeRemoteCosmetic next) {
        cachedPlayers.add(uuid);
        phazeStates.compute(uuid, (ignored, current) ->
                current == null || next.updatedAt >= current.updatedAt ? next : current);
    }

    private static PhazeRemoteCosmetic decodePhaze(String value, boolean equipped, long updatedAt) {
        String[] slots = value == null ? new String[0] : value.split("\\|", -1);
        int[] ids = {-1, -1, -1, -1, -1};
        for (int i = 0; i < Math.min(ids.length, slots.length); i++) {
            try { ids[i] = Integer.parseInt(slots[i]); }
            catch (NumberFormatException ignored) { }
        }
        return new PhazeRemoteCosmetic(ids, equipped, updatedAt, System.currentTimeMillis());
    }

    private static RemoteCosmetic decode(String value, boolean equipped, long updatedAt) {
        String raw = value == null ? CosmeticsState.NONE : value;
        String[] slots = raw.split("\\Q" + SLOT_SEPARATOR + "\\E", -1);
        if (slots.length >= 2) {
            String wing = slots[0];
            String cape = slots[1];
            String hat = slots.length >= 3 ? slots[2] : CosmeticsState.NONE;
            String pet = slots.length >= 4 ? slots[3] : CosmeticsState.NONE;
            return new RemoteCosmetic(
                    wing.isBlank() ? CosmeticsState.NONE : wing,
                    cape.isBlank() ? CosmeticsState.NONE : cape,
                    hat.isBlank() ? CosmeticsState.NONE : hat,
                    pet.isBlank() ? CosmeticsState.NONE : pet,
                    equipped,
                    updatedAt,
                    System.currentTimeMillis()
            );
        }

        boolean cape = CosmeticsState.isCape(raw);
        return new RemoteCosmetic(
                cape ? CosmeticsState.NONE : raw,
                cape ? raw : CosmeticsState.NONE,
                CosmeticsState.NONE,
                CosmeticsState.NONE,
                equipped,
                updatedAt,
                System.currentTimeMillis()
        );
    }

    private record RemoteCosmetic(
            String wing,
            String cape,
            String hat,
            String pet,
            boolean equipped,
            long updatedAt,
            long receivedAt
    ) {
    }

    private record PhazeRemoteCosmetic(int[] ids, boolean equipped, long updatedAt, long receivedAt) {
        int id(vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory category) {
            int index = switch (category) {
                case WINGS -> 0;
                case CAPE -> 1;
                case HAT -> 2;
                case BODYWEAR -> 3;
                case PET -> 4;
                default -> -1;
            };
            return index < 0 ? -1 : ids[index];
        }
    }

    private record GraffitiQuery(String serverKey, String dimension, int epoch) { }

    private static final class HttpStatusException extends Exception {
        final int status;

        HttpStatusException(int status) {
            super("HTTP " + status, null, false, false);
            this.status = status;
        }
    }

    public record PhazeGraffiti(
            String serverKey,
            String dimension,
            BlockPos pos,
            Direction face,
            int graffitiId
    ) { }
}
