create table terrang.my_riks tablespace lm as
select * from terrang.my_middle
union select * from terrang.my_north
union select * from terrang.my_south;
