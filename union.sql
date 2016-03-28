-- Skapa union av regionala dataset samt ny primärnyckel och spatialt index.
-- OBS! Tar även bort regionala dataset
-- ds = datasetnamn
create table terrang.${ds}_riks tablespace lm as
  select kkod,kategori,geom from terrang.${ds}_middle
  union select kkod,kategori,geom from terrang.${ds}_north
  union select kkod,kategori,geom from terrang.${ds}_south;
alter table terrang.${ds}_riks add column gid serial primary key;
create index on terrang.${ds}_riks using gist (geom) tablespace lm;
drop table terrang.${ds}_middle;
drop table terrang.${ds}_north;
drop table terrang.${ds}_south;
