ALTER TABLE resource
ADD COLUMN ownerUserId varchar(255),
ADD COLUMN modifierUserId varchar(255);
-- NB: don't set constraint back
