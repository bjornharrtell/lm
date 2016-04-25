package org.wololo.postgis2tiles;

import java.util.Properties;

import org.geowebcache.grid.BoundingBox;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class PostGIS {
	static final Logger logger = LoggerFactory.getLogger(PostGIS.class);

	static final HikariDataSource ds;

	static {
		final Properties props = new Properties();
		props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
		props.setProperty("dataSource.serverName", "localhost");
		props.setProperty("dataSource.user", "postgres");
		props.setProperty("dataSource.password", "postgres");
		props.setProperty("dataSource.databaseName", "lm");
		final HikariConfig config = new HikariConfig(props);
		ds = new HikariDataSource(config);
	}

	public static Result<?> fetch(final String layerName, final BoundingBox bbox) {
		final DSLContext ctx = DSL.using(ds, SQLDialect.POSTGRES_9_4);
		ctx.settings().setExecuteLogging(false);
		// ctx.settings().setRenderFormatted(true);
		// ctx.settings().setParamType(ParamType.INLINED);

		final double xmin = bbox.getMinX();
		final double ymin = bbox.getMinY();
		final double xmax = bbox.getMaxX();
		final double ymax = bbox.getMaxY();

		final Field<Object> geom = DSL.field("geom");
		final Field<Object> wkb = DSL.function("ST_AsEWKB", Object.class, geom).as("wkb");
		final Field<Boolean> makeEnvelope = DSL.function("ST_MakeEnvelope", boolean.class, DSL.val(xmin), DSL.val(ymin),
				DSL.val(xmax), DSL.val(ymax), DSL.val(3006));
		final Field<Boolean> intersects = DSL.function("ST_Intersects", boolean.class, geom, makeEnvelope);

		if (layerName == "tx_riks") {
			return ctx.select(DSL.field("kkod"), DSL.field("text"), wkb).from("terrang." + layerName).where(intersects).fetch();
		} else {
			return ctx.select(DSL.field("kkod"), wkb).from("terrang." + layerName).where(intersects).fetch();
		}
	}
}
