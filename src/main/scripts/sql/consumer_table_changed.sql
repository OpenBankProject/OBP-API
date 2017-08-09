alter table consumer alter column apptype type varchar(10);
update consumer set apptype = 'Web' where apptype='0';
update consumer set apptype = 'Mobile' where apptype='1';