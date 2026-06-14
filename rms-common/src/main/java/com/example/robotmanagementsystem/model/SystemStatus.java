package com.example.robotmanagementsystem.model;

import java.util.List;

public record SystemStatus(String status, List<ComponentStatus> components) {
}
