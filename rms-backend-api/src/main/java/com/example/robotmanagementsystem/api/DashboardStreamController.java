package com.example.robotmanagementsystem.api;

import com.example.robotmanagementsystem.service.DashboardStreamService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class DashboardStreamController {

    private final DashboardStreamService dashboardStreamService;

    public DashboardStreamController(DashboardStreamService dashboardStreamService) {
        this.dashboardStreamService = dashboardStreamService;
    }

    @GetMapping(path = "/dashboard", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> dashboard(HttpServletResponse response) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(dashboardStreamService.registerEmitter());
    }
}
