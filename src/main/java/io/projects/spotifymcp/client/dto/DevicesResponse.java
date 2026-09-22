package io.projects.spotifymcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DevicesResponse(List<Device> devices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Device(String id, Boolean isActive, String name, String type, Integer volumePercent) {
    }
}
