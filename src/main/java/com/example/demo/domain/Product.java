package com.example.demo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends BaseEntity{

    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;


}
