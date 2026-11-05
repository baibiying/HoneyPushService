package com.honeypush.service.media;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class VideoProxyService {
    private static final String[] FORWARD_HEADERS = {
        "content-type", "content-length", "content-range", "accept-ranges", "cache-control"
    };

    private final RestClient restClient;

    public VideoProxyService() {
        this.restClient = RestClient.create();
    }

    public ResponseEntity<byte[]> proxyVideoStream(String videoUrl, String rangeHeader) {
        RestClient.RequestHeadersSpec<?> spec = restClient
                .get()
                .uri(videoUrl)
                .header("User-Agent", BilibiliService.BILIBILI_UA)
                .header("Referer", BilibiliService.BILIBILI_REFERER);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            spec = spec.header(HttpHeaders.RANGE, rangeHeader);
        }

        return spec.exchange((request, response) -> {
            HttpHeaders headers = new HttpHeaders();
            for (String key : FORWARD_HEADERS) {
                String val = response.getHeaders().getFirst(key);
                if (val != null) {
                    headers.set(key, val);
                }
            }
            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
            if (contentType == null || !contentType.startsWith("video")) {
                headers.set(HttpHeaders.CONTENT_TYPE, "video/mp4");
            }
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            byte[] body = response.getBody().readAllBytes();
            return new ResponseEntity<>(body, headers, HttpStatus.valueOf(response.getStatusCode().value()));
        });
    }
}
