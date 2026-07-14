CREATE TABLE order_summary (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    item_count INT NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL
);