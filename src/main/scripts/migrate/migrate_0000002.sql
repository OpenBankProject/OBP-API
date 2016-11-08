alter table users add column username type varchar(64);
CREATE UNIQUE INDEX unq_username ON users (username);