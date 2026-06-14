package com.example.robotmanagementsystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RobotApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsEmptyRobotListBeforeRos2TelemetryArrives() throws Exception {
        mockMvc.perform(get("/api/robots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsNotFoundBeforeRos2TelemetryRegistersRobot() throws Exception {
        mockMvc.perform(get("/api/robots/robot-001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingRobot() throws Exception {
        mockMvc.perform(get("/api/robots/robot-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robotTotal").value(0))
                .andExpect(jsonPath("$.onlineRobots").exists())
                .andExpect(jsonPath("$.averageTemperature").exists())
                .andExpect(jsonPath("$.redisAlertCount").exists())
                .andExpect(jsonPath("$.openAlertCount").exists());
    }
}
