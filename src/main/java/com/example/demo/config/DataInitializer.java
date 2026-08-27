package com.example.demo.config;

import com.example.demo.model.Delivery;
import com.example.demo.model.DeliveryStatus;
import com.example.demo.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DeliveryRepository deliveryRepository;

    @Override
    public void run(String... args) throws Exception {
        if (deliveryRepository.count() == 0) {
            log.info("[SmartHub DataInitializer] Seeding initial deliveries into database...");

            deliveryRepository.save(Delivery.builder()
                    .trackingCode("RK-2026-001")
                    .customerName("Nguyễn Văn A")
                    .hubCode("HN-01")
                    .status(DeliveryStatus.IN_TRANSIT)
                    .codAmount(new BigDecimal("500000"))
                    .build());

            deliveryRepository.save(Delivery.builder()
                    .trackingCode("RK-2026-002")
                    .customerName("Trần Thị B")
                    .hubCode("HCM-01")
                    .status(DeliveryStatus.DELIVERED)
                    .codAmount(new BigDecimal("1200000"))
                    .build());

            deliveryRepository.save(Delivery.builder()
                    .trackingCode("RK-2026-003")
                    .customerName("Lê Văn C")
                    .hubCode("HN-01")
                    .status(DeliveryStatus.DELAYED)
                    .codAmount(new BigDecimal("350000"))
                    .build());

            deliveryRepository.save(Delivery.builder()
                    .trackingCode("RK-2026-004")
                    .customerName("Phạm Minh D")
                    .hubCode("DN-01")
                    .status(DeliveryStatus.IN_TRANSIT)
                    .codAmount(new BigDecimal("890000"))
                    .build());

            deliveryRepository.save(Delivery.builder()
                    .trackingCode("RK-2026-005")
                    .customerName("Hoàng Anh E")
                    .hubCode("HCM-01")
                    .status(DeliveryStatus.DAMAGED)
                    .codAmount(new BigDecimal("2100000"))
                    .build());

            log.info("[SmartHub DataInitializer] Successfully seeded 5 initial deliveries.");
        }
    }
}
