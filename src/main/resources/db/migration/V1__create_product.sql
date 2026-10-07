CREATE TABLE product (

    id  BIGSERIAL PRIMARY KEY ,
    code VARCHAR(15) NOT NULL UNIQUE,
    description VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    quantity NUMERIC(10, 3) NOT NULL,
    unit VARCHAR(2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE

);