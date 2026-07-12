CREATE TABLE product (
                         id UUID PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         description TEXT,
                         price NUMERIC(19, 2) NOT NULL,
                         stock_quantity INT NOT NULL,
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                         updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE orders (
                        id UUID PRIMARY KEY,
                        customer_id UUID NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        total_amount NUMERIC(19, 2) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE order_item (
                            id UUID PRIMARY KEY,
                            order_id UUID NOT NULL REFERENCES orders(id),
                            product_id UUID NOT NULL REFERENCES product(id),
                            quantity INT NOT NULL,
                            unit_price NUMERIC(19, 2) NOT NULL,
                            subtotal NUMERIC(19, 2) NOT NULL,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);