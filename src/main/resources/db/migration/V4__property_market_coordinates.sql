ALTER TABLE property_market_transaction
    ADD COLUMN latitude DECIMAL(10,7) NULL AFTER road_name;

ALTER TABLE property_market_transaction
    ADD COLUMN longitude DECIMAL(10,7) NULL AFTER latitude;

ALTER TABLE property_market_transaction
    ADD KEY idx_property_market_coordinates (latitude, longitude);