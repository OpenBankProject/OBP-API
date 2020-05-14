-- the index DDL is: CREATE UNIQUE INDEX dynamicentity_metadatajson ON public.dynamicentity USING btree (metadatajson);
-- it is not be used now, and btree type index have length limit of 2704, So just delete this index.
DROP INDEX public.dynamicentity_metadatajson;
