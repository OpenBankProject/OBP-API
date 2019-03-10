ALTER TABLE "mappedcounterpartymetadata" ALTER COLUMN "counterpartyid" type varchar(44);
ALTER TABLE "mappedcounterparty" ALTER COLUMN "mcounterpartyid" type varchar(44);
ALTER TABLE "mappedtransaction" ALTER COLUMN "cpcounterpartyid" type varchar(44);