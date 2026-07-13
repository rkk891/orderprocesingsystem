CREATE TABLE orders (
    id uuid CONSTRAINT pk_orders PRIMARY KEY,
    status varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_timestamps
        CHECK (updated_at >= created_at)
);

CREATE TABLE order_items (
    id bigint GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_order_items PRIMARY KEY,
    order_id uuid NOT NULL,
    position smallint NOT NULL,
    product_id varchar(100) NOT NULL,
    quantity smallint NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uq_order_items_product UNIQUE (order_id, product_id),
    CONSTRAINT uq_order_items_position UNIQUE (order_id, position),
    CONSTRAINT ck_order_items_product
        CHECK (char_length(product_id) BETWEEN 1 AND 100 AND btrim(product_id) <> ''),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT ck_order_items_position CHECK (position BETWEEN 0 AND 99)
);

CREATE INDEX idx_orders_created_id
    ON orders (created_at DESC, id DESC);

CREATE INDEX idx_orders_status_created_id
    ON orders (status, created_at DESC, id DESC);
