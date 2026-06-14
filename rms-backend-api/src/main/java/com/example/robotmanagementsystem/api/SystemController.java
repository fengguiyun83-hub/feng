package com.example.robotmanagementsystem.api;

import com.example.robotmanagementsystem.model.SystemStatus;
import com.example.robotmanagementsystem.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatusService systemStatusService;

    public SystemController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        return systemStatusService.status();
    }
}
