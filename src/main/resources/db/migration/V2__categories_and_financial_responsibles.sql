CREATE TABLE categories (
      id                    UUID NOT NULL PRIMARY KEY,
      user_id               UUID NOT NULL REFERENCES users(id),
      name                  VARCHAR(255) NOT NULL,
      description           VARCHAR(500),
      created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE financial_responsible (
    id                    UUID NOT NULL PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES  users(id),
    name                  VARCHAR(255) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE INDEX idx_financial_responsible ON financial_responsible(user_id);