package org.wololo.postgis2tiles;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.geowebcache.grid.BoundingBox;
/*
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class PostGIS {
	static final Logger logger = LoggerFactory.getLogger(PostGIS.class);

	static final HikariDataSource ds;

	static {
		/*
		final Properties props = new Properties();
		props.setProperty("driverClassName", "org.postgis.DriverWrapper");
		props.setProperty("dataSource.serverName", "localhost");
		props.setProperty("dataSource.user", "postgres");
		props.setProperty("dataSource.password", "postgres");
		props.setProperty("dataSource.databaseName", "lm");
		final HikariConfig config = new HikariConfig(props);
		*/
		
		ds = new HikariDataSource();
		ds.setJdbcUrl("jdbc:postgresql://localhost/lm");
		ds.setUsername("postgres");
		ds.setPassword("postgres");
	}
	
	public static class LMFeature {
		LMFeature(Geometry geom, int kkod, String text) {
			this.geom = geom;
			this.kkod = kkod;
			this.text = text;
		}
		
		LMFeature(Geometry geom, int kkod) {
			this(geom, kkod, null);
		}
		
		public Geometry geom;
		public int kkod;
		public String text;
	}
	
	public static List<LMFeature> fetch2(String layerName, final BoundingBox bbox) {
		try (Connection conn = ds.getConnection()){
			//String text = layerName == "tx_riks" ? "text," : "";
			String text = "";
			
			PreparedStatement statement = conn.prepareStatement("select kkod,"+ text +"ST_AsEWKB(geom) as geom from terrang." + layerName + " where " +
					"ST_Intersects(geom, ST_MakeEnvelope(?,?,?,?,?))");
			statement.setInt(1, (int) bbox.getMinX());
			statement.setInt(2, (int) bbox.getMinY());
			statement.setInt(3, (int) bbox.getMaxX());
			statement.setInt(4, (int) bbox.getMaxY());
			statement.setInt(5, 3006);
			
			ResultSet result = statement.executeQuery();
			
			List<LMFeature> features = new ArrayList<LMFeature>();
			while (result.next()) {
				int kkod = result.getInt(1);
				byte[] wkb = result.getBytes(2);
				Geometry geom = parseWKB(wkb);
				LMFeature feature = new LMFeature(geom, kkod);
				features.add(feature);
			}
			return features;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static final ThreadLocal<WKBReader> reader = new ThreadLocal<WKBReader>() {
        @Override protected WKBReader initialValue() {
            return new WKBReader();
        }
    };
	
	public static Geometry parseWKB(byte[] bytes) {
		Geometry geometry;
		try {
			geometry = reader.get().read(bytes);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return geometry;
	}

	/*
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
	*/
}
