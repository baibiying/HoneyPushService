package com.honeypush.service.web;

import com.honeypush.service.media.BilibiliService;
import com.honeypush.service.media.VideoProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/officer-preview-video")
public class OfficerPreviewVideoController {

    private final BilibiliService bilibiliService;
    private final VideoProxyService videoProxyService;

    public OfficerPreviewVideoController(BilibiliService bilibiliService, VideoProxyService videoProxyService) {
        this.bilibiliService = bilibiliService;
        this.videoProxyService = videoProxyService;
    }

    @GetMapping
    ResponseEntity<?> get(@RequestParam(value = "bvid", required = false) String bvid, @RequestHeader(value = "Range", required = false) String range) {
        if (bvid == null || bvid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing bvid"));
        }
        try {
            String videoUrl = bilibiliService.resolveMp4Url(bvid);
            return videoProxyService.proxyVideoStream(videoUrl, range);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "PREVIEW_RESOLVE_FAILED";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", message));
        }
    }
}
