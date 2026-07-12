package com.example.demo.repository;

import com.example.demo.config.JpaConfig;
import com.example.demo.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager em;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        Order order1 = new Order();
        order1.setCustomerId(customerId);
        order1.setStatus(OrderStatus.CREATED);
        order1.setTotalAmount(BigDecimal.valueOf(100));
        em.persistAndFlush(order1);

        Order order2 = new Order();
        order2.setCustomerId(customerId);
        order2.setStatus(OrderStatus.CONFIRMED);
        order2.setTotalAmount(BigDecimal.valueOf(200));
        em.persistAndFlush(order2);

        Order otherCustomer = new Order();
        otherCustomer.setCustomerId(UUID.randomUUID());
        otherCustomer.setStatus(OrderStatus.CREATED);
        otherCustomer.setTotalAmount(BigDecimal.valueOf(50));
        em.persistAndFlush(otherCustomer);
    }

    @Test
    void findByCustomerIdOrderByCreatedAtDesc_returnsOnlyCustomerOrders() {
        List<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);

        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.getCustomerId().equals(customerId));
    }

    @Test
    void findByStatus_returnsMatchingOrders() {
        List<Order> created = orderRepository.findByStatus(OrderStatus.CREATED);

        assertThat(created).hasSize(2);
        assertThat(created).allMatch(o -> o.getStatus() == OrderStatus.CREATED);
    }

    @Test
    void findByStatusAndDateRange_returnsPagedResults() {
        Instant from = Instant.now().minusSeconds(60);
        Instant to = Instant.now().plusSeconds(60);

        Page<Order> page = orderRepository.findByStatusAndDateRange(
                OrderStatus.CREATED, from, to, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findWithItemsById_loadsOrderItemsEagerly() {
        Product product = new Product();
        product.setName("Widget");
        product.setDescription("A widget");
        product.setPrice(BigDecimal.valueOf(10));
        product.setStockQuantity(100);
        em.persistAndFlush(product);

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.valueOf(20));

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(10));
        item.setSubtotal(BigDecimal.valueOf(20));
        order.getOrderItems().add(item);

        em.persistAndFlush(order);
        em.clear();

        Optional<Order> found = orderRepository.findWithItemsById(order.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOrderItems()).hasSize(1);
    }
}
