package com.example.demo.repository;

import com.example.demo.domain.OrderSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OrderSummaryRepository extends JpaRepository<OrderSummary, UUID>,
        JpaSpecificationExecutor<OrderSummary> {

    Optional<OrderSummary> findByOrderId(UUID orderId);

}
