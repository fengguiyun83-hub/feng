package com.example.robotmanagementsystem;

import com.example.robotmanagementsystem.service.DashboardStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardStreamService dashboardStreamService;

    @Test
    void dashboardStreamStartsAsyncSseResponse() throws Exception {
        mockMvc.perform(get("/api/stream/dashboard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(request().asyncStarted());
    }

    @Test
    void registerAndRemoveEmitter() {
        int before = dashboardStreamService.activeConnectionCount();
        SseEmitter emitter = dashboardStreamService.registerEmitter();

        assertThat(dashboardStreamService.activeConnectionCount()).isEqualTo(before + 1);

        dashboardStreamService.removeEmitter(emitter);

        assertThat(dashboardStreamService.activeConnectionCount()).isEqualTo(before);
    }
}
