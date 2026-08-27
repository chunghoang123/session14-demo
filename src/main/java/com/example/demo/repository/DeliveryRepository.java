package com.example.demo.repository;

import com.example.demo.model.Delivery;
import com.example.demo.model.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByTrackingCode(String trackingCode);
    List<Delivery> findByHubCode(String hubCode);
    List<Delivery> findByStatus(DeliveryStatus status);
    List<Delivery> findByHubCodeAndStatus(String hubCode, DeliveryStatus status);
}
