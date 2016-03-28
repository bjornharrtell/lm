-- Skapa värdetabell av kategorier i källa, ta bort kolumnen från källan samt skapa främmande nyckel till värdetabell.
create table terrang.${ds}_riks_kategori tablespace lm as
  select kkod, kategori from terrang.${ds}_riks group by kkod, kategori order by kkod;
alter table terrang.${ds}_riks_kategori add primary key (kkod);
alter table terrang.${ds}_riks drop column kategori;
create index on terrang.${ds}_riks (kkod asc nulls last) tablespace lm;
alter table terrang.${ds}_riks
  add foreign key (kkod) references terrang.${ds}_riks_kategori (kkod)
   on update no action on delete no action;
