UPDATE property_listing
SET maintenance_fee = ROUND(maintenance_fee / 10000)
WHERE source_type = 'USER' AND maintenance_fee >= 10000;
