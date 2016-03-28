#!/bin/sh
# Kör union.sql för samtliga regionala dataset.
ds=ma envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=mb envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=ml envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=mo envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=ms envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=mv envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=mx envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=my envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=od envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=oh envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=os envsubst < union.sql | psql -U postgres -h localhost -1 lm
ds=ot envsubst < union.sql | psql -U postgres -h localhost -1 lm
