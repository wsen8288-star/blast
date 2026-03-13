package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "collection_settings")
public class CollectionSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "frequency", nullable = false)
    private String frequency;
    
    @Column(name = "storage_path", nullable = false)
    private String storagePath;
    
    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "furnace_id")
    private String furnaceId;

    @Column(name = "script_template_key")
    private String scriptTemplateKey;

    @Column(name = "script_seed")
    private Long scriptSeed;
    
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
}
