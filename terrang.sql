create table terrang.my_riks tablespace lm as
select * from terrang.my_middle
union select * from terrang.my_north
union select * from terrang.my_south;

create table terrang.my_riks_kategori as select kkod, kategori from terrang.my_riks group by kkod, kategori order by kkod;
ALTER TABLE terrang.my_riks_kategori ADD PRIMARY KEY (kkod);
alter table terrang.my_riks drop column kategori;
