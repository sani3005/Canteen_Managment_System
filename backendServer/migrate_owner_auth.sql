-- Run once on a database created by an older schema.
USE canteen_db;

ALTER TABLE users
    MODIFY username VARCHAR(150) NOT NULL;
