package org.wololo.postgis2tiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier;

import no.ecc.vectortile.VectorTileEncoder;

public class VectorTileStreamer {
	static final Logger logger = LoggerFactory.getLogger(VectorTileStreamer.class);
	
	static double[] res = new double[] { 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5 };
	public static BoundingBox sweref99tmbbox = new BoundingBox(218128, 6126002, 1083427, 7692850);
	static GridSet gridSet = GridSetFactory.createGridSet("sweref99tm", SRS.getSRS(3006), sweref99tmbbox, false, res, null,
			null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, null, 256, 256, false);
	
	static public class TileCoord {
		public TileCoord(final long[] t) {
			this.t = t;
		}
		public final long[] t;
	}
	
	static void stream() throws IOException {
		logger.info("Streaming starts...");
		
		GridSubset gridSubset = GridSubsetFactory.createGridSubSet(gridSet, sweref99tmbbox, 0, 6);
		
		TileRange tr = new TileRange("", "subset", 0, 6, gridSubset.getCoverages(), null, null, null);
		final TileRangeIterator it = new TileRangeIterator(tr, new int[] { 1, 1 });
		
		Iterator<TileCoord> it2 = new Iterator<TileCoord>() {
			boolean hasNext = true;
			public boolean hasNext() { return hasNext; }
			public TileCoord next() {
				long[] nextItem = it.nextMetaGridLocation(new long[3]);
				if (nextItem == null) {
					hasNext = false;
					return null;
				}
				return new TileCoord(nextItem);
			}
		};
		
		Iterable<TileCoord> iterable = () -> it2;
		Stream<TileCoord> targetStream = StreamSupport.stream(iterable.spliterator(), false);
		
		targetStream.parallel().forEach(t -> streamTile(gridSubset, t == null ? null : t.t));
		
		logger.info("Streaming ends...");
	}
	
	static String tileIndexToString(long[] tileIndex) {
		long x = tileIndex[0];
		long y = tileIndex[1];
		long z = tileIndex[2];
		return z + "/" + x + "/" + y;
	}
	
	public static Envelope toEnvelope(BoundingBox bbox) {
		return new Envelope(bbox.getMinX(), bbox.getMaxX(), bbox.getMinY(), bbox.getMaxY());
	}
	
	public static Geometry parseWKB(byte[] bytes) {
		WKBReader reader = new WKBReader();
		Geometry geometry;
		try {
			geometry = reader.read(bytes);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return geometry;
	}
	
	static void streamTile(GridSubset gridSubset, long[] tileIndex) {
		if (tileIndex == null) return;
		
		BoundingBox tileBounds = gridSubset.boundsFromIndex(tileIndex);
		Envelope envelope = toEnvelope(tileBounds);
		long z = tileIndex[2];

		// logger.info("Stream tile at " + tileIndexToString(tileIndex));

		String name = "terrang";
		
		VectorTileEncoder encoder = new VectorTileEncoder();
		PostGIS.fetch("al_riks", tileBounds).forEach(r -> {
			Geometry geom = parseWKB(r.getValue("wkb", byte[].class));
			
			if (!geom.getEnvelopeInternal().intersects(envelope)) return;
			
			geom.apply(new CoordinateFilter() {
				@Override
				public void filter(Coordinate c) {
					c.x = (c.x - tileBounds.getMinX()) / res[(int) z];
					c.y = 256 - ((c.y - tileBounds.getMinY()) / res[(int) z]);
				}
			});
			geom = TopologyPreservingSimplifier.simplify(geom, 0.1);
			
			Map<String, Object> attributes = r.intoMap();
			attributes.remove("wkb");
			
			encoder.addFeature(name, attributes, geom);
		});
		
		byte[] mbtile = encoder.encode();
		
		String path = name + "/" + tileIndexToString(tileIndex) + ".pbf";
		try {
			Files.createDirectories(Paths.get(path).getParent());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		File file = new File(path);
		try (FileOutputStream fos = new FileOutputStream(file)){
			fos.getChannel().write(ByteBuffer.wrap(mbtile));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}