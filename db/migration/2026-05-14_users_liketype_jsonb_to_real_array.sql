-- Active: 1778681538726@@127.0.0.1@5432@knowledge_planet

BEGIN;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS liketype_arr real[];


ALTER TABLE users DROP COLUMN IF EXISTS liketype;
ALTER TABLE users RENAME COLUMN liketype_arr TO liketype;

ALTER TABLE users
ALTER COLUMN liketype SET DEFAULT ARRAY[0.125,0.125,0.125,0.125,0.125,0.125,0.125,0.125]::real[];

ALTER TABLE users
ADD CONSTRAINT ck_users_liketype_dim_8
CHECK (array_length(liketype, 1) = 8);

COMMIT;
