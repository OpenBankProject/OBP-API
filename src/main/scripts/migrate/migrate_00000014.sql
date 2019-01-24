ALTER TABLE consumer ADD persecondcalllimit bigint;
UPDATE consumer SET persecondcalllimit = -1;