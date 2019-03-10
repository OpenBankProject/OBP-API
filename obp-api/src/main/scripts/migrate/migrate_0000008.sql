alter table consumer alter column apptype type varchar(10);
update consumer set apptype = 'Web' where apptype='0';
update consumer set apptype = 'Mobile' where apptype='1';

alter table token alter column tokentype type varchar(10);
update token set tokentype = 'Request' where tokentype='0';
update token set tokentype = 'Access' where tokentype='1';