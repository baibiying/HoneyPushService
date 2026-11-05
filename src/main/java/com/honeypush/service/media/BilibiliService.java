package com.honeypush.service.media;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class BilibiliService {
    public static final String BILIBILI_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String BILIBILI_REFERER = "https://www.bilibili.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BilibiliService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String resolveMp4Url(String bvid) {
        String viewJson = restClient
                .get()
                .uri("https://api.bilibili.com/x/web-interface/view?bvid="
                        + URLEncoder.encode(bvid, StandardCharsets.UTF_8))
                .header("User-Agent", BILIBILI_UA)
                .retrieve()
                .body(String.class);

        JsonNode view;
        try {
            view = objectMapper.readTree(viewJson);
        } catch (Exception e) {
            throw new IllegalStateException("BILIBILI_VIEW_FAILED", e);
        }
        if (view.path("code").asInt(-1) != 0 || !view.path("data").has("cid")) {
            throw new IllegalStateException(view.path("message").asText("BILIBILI_VIEW_FAILED"));
        }
        long cid = view.path("data").path("cid").asLong();

        String playJson = restClient
                .get()
                .uri("https://api.bilibili.com/x/player/playurl?bvid="
                        + URLEncoder.encode(bvid, StandardCharsets.UTF_8) + "&cid=" + cid + "&qn=32&fnval=1")
                .header("User-Agent", BILIBILI_UA)
                .header("Referer", BILIBILI_REFERER)
                .retrieve()
                .body(String.class);

        JsonNode play;
        try {
            play = objectMapper.readTree(playJson);
        } catch (Exception e) {
            throw new IllegalStateException("BILIBILI_PLAYURL_FAILED", e);
        }
        if (play.path("code").asInt(-1) != 0) {
            throw new IllegalStateException(play.path("message").asText("BILIBILI_PLAYURL_FAILED"));
        }
        String durl = play.path("data").path("durl").path(0).path("url").asText(null);
        if (durl != null && !durl.isBlank()) {
            return durl;
        }
        String dash = play.path("data").path("dash").path("video").path(0).path("baseUrl").asText(null);
        if (dash != null && !dash.isBlank()) {
            return dash;
        }
        throw new IllegalStateException("BILIBILI_NO_PLAYABLE_URL");
    }
}
