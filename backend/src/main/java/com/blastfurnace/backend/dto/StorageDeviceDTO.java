package com.blastfurnace.backend.dto;

import lombok.Data;

@Data
public class StorageDeviceDTO {
    private Long id;
    private String name;
    private String type;
    private String status;
    private int capacity;
    private int used;
    private int remaining;
    private int usage;
}