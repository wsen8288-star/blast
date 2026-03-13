package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.CollectionDevice;
import com.blastfurnace.backend.repository.CollectionDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CollectionDeviceService {
    @Autowired
    private CollectionDeviceRepository collectionDeviceRepository;
    
    public List<CollectionDevice> findAll() {
        return collectionDeviceRepository.findAll();
    }
    
    public CollectionDevice findById(Long id) {
        return collectionDeviceRepository.findById(id).orElse(null);
    }
    
    public CollectionDevice save(CollectionDevice device) {
        return collectionDeviceRepository.save(device);
    }
    
    public void delete(Long id) {
        collectionDeviceRepository.deleteById(id);
    }
    
    public long count() {
        return collectionDeviceRepository.count();
    }
}
