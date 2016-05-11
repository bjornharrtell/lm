package org.wololo.postgis2tiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
// import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wololo.postgis2tiles.PostGIS.LMFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

import no.ecc.vectortile.VectorTileEncoder;

public class VectorTileStreamer {
	private static final Logger logger = LoggerFactory.getLogger(VectorTileStreamer.class);

	private static final int extent = 256;
	
	private static final int zoomStart = 0;
	private static final int zoomEnd = 8;

	private static final double[] res = new double[] { 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5 };
	private static final BoundingBox sweref99tmbbox = new BoundingBox(218128, 6126002, 1083427, 7692850);
	private static final GridSet gridSet = GridSetFactory.createGridSet("sweref99tm", SRS.getSRS(3006), sweref99tmbbox, false, res,
			null, null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, null, 256, 256, false);
	
	private static final ThreadLocal<WKBReader> reader = new ThreadLocal<WKBReader>() {
        @Override protected WKBReader initialValue() {
            return new WKBReader();
        }
    };

	private static final class TileIndexWrapper {
		public TileIndexWrapper(final long[] t) {
			this.t = t;
		}

		public final long[] t;
	}

	static void stream() throws IOException {
		logger.info("Streaming starts...");

		GridSubset gridSubset = GridSubsetFactory.createGridSubSet(gridSet, sweref99tmbbox, zoomStart, zoomEnd);

		
		TileRange tr = new TileRange("", "subset", zoomStart, zoomEnd, gridSubset.getCoverages(), null, null, null);
		final TileRangeIterator it = new TileRangeIterator(tr, new int[] { 1, 1 });

		/*
		Iterator<TileIndexWrapper> it2 = new Iterator<TileIndexWrapper>() {
			boolean hasNext = true;

			public boolean hasNext() {
				return hasNext;
			}

			public TileIndexWrapper next() {
				long[] nextItem = it.nextMetaGridLocation(new long[3]);
				if (nextItem == null) {
					hasNext = false;
					return null;
				}
				return new TileIndexWrapper(nextItem);
			}
		};

		Iterable<TileIndexWrapper> iterable = () -> it2;
		Stream<TileIndexWrapper> targetStream = StreamSupport.stream(iterable.spliterator(), false);

		targetStream.parallel().forEach(t -> streamTile(gridSubset, t == null ? null : t.t));
		*/
		logger.info("Streaming ends...");
	}

	static String tileIndexToString(long[] tileIndex) {
		final long x = tileIndex[0];
		final long y = tileIndex[1];
		final long z = tileIndex[2];
		return z + "/" + x + "/" + y;
	}

	public static Envelope toEnvelope(BoundingBox bbox) {
		return new Envelope(bbox.getMinX(), bbox.getMaxX(), bbox.getMinY(), bbox.getMaxY());
	}

	public static Geometry parseWKB(byte[] bytes) {
		Geometry geometry;
		try {
			geometry = reader.get().read(bytes);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return geometry;
	}

	static void encodeRecord(String name, Envelope e, double res, VectorTileEncoder encoder, LMFeature f) {
		if (name == "vl_riks" && f.kkod >= 5044 && f.kkod <= 5091 && res > 16) return;
		
		f.geom.apply(new CoordinateFilter() {
			@Override
			public void filter(Coordinate c) {
				c.x = (c.x - e.getMinX()) / res;
				c.y = extent - ((c.y - e.getMinY()) / res) - 1;
			}
		});
		final Geometry simplifiedGeom = DouglasPeuckerSimplifier.simplify(f.geom, 0.5);

		final Map<String, Object> attributes = new HashMap<String, Object>();
		attributes.put("kkod", f.kkod);

		encoder.addFeature(name, attributes, simplifiedGeom);
	}

	static void streamTile(GridSubset gridSubset, long[] tileIndex) {
		if (tileIndex == null)
			return;

		final BoundingBox tileBounds = gridSubset.boundsFromIndex(tileIndex);
		final Envelope envelope = toEnvelope(tileBounds);
		final long z = tileIndex[2];
		final double resolution = res[(int) z];

		// logger.info("Stream tile at " + tileIndexToString(tileIndex));

		
		final VectorTileEncoder encoder = new VectorTileEncoder(512, 8, true);
		
		/*
		PostGIS.fetch2("al_riks", tileBounds).forEach(f -> encodeRecord("al_riks", envelope, resolution, encoder, f));
		if (z > 5) PostGIS.fetch2("vl_riks", tileBounds).forEach(f -> encodeRecord("vl_riks", envelope, resolution, encoder, f));
		// if (z > 5) PostGIS.fetch2("tx_riks", tileBounds).forEach(r -> encodeRecord("tx_riks", envelope, resolution, encoder, r));
		if (z > 5) PostGIS.fetch2("by_riks", tileBounds).forEach(f -> encodeRecord("by_riks", envelope, resolution, encoder, f));
		if (z > 5) PostGIS.fetch2("my_riks", tileBounds).forEach(f -> encodeRecord("my_riks", envelope, resolution, encoder, f));
		// if (z > 7) PostGIS.fetch2("oh_riks", tileBounds).forEach(f -> encodeRecord("oh_riks", envelope, resolution, encoder, f)); 
		 */
		final byte[] mbtile = encoder.encode();

		final String path = "terrang/" + tileIndexToString(tileIndex) + ".pbf";
		try {
			Files.createDirectories(Paths.get(path).getParent());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		final File file = new File(path);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.getChannel().write(ByteBuffer.wrap(mbtile));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
