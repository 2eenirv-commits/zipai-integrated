-- Record ownership for room-connect data created by authenticated users.
-- Existing legacy rows remain unassigned and are still available to admin tooling only.

ALTER TABLE room_visit
ADD COLUMN requester_user_id BIGINT NULL AFTER visit_id;

ALTER TABLE room_visit
ADD KEY idx_room_visit_requester (requester_user_id, visit_id);

ALTER TABLE room_offer
ADD COLUMN owner_user_id BIGINT NULL AFTER offer_id;

ALTER TABLE room_offer
ADD KEY idx_room_offer_owner (owner_user_id, offer_id);
