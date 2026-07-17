package com.example.demo.metrics;

import com.example.demo.domain.OrderStatus;
import com.example.demo.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderMetrics {

    private final MeterRegistry registry;
    private final OrderRepository orderRepository;

    @PostConstruct
    public void registerGauges() {
        for (OrderStatus status : OrderStatus.values()) {
            Gauge.builder("orders.by.status", orderRepository, r -> r.countByStatus(status))
                    .tag("status", status.name())
                    .description("Number of orders per status")
                    .register(registry);
        }
    }

    public void recordOrderCreated() {
        registry.counter("orders.created.total").increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(registry.timer("order.processing.time"));
    }
}
