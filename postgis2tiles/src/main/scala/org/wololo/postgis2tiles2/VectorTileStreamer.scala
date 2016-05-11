package org.wololo.postgis2tiles2

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.HashMap
import java.util.Iterator
import java.util.Map
import java.util.stream.Stream
import java.util.stream.StreamSupport
import org.geowebcache.grid.BoundingBox
import org.geowebcache.grid.GridSet
import org.geowebcache.grid.GridSetFactory
import org.geowebcache.grid.GridSubset
import org.geowebcache.grid.GridSubsetFactory
import org.geowebcache.grid.SRS
import org.geowebcache.storage.TileRange
import org.geowebcache.storage.TileRangeIterator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.wololo.postgis2tiles.PostGIS.LMFeature
import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.CoordinateFilter
import com.vividsolutions.jts.geom.Envelope
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKBReader
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier
import no.ecc.vectortile.VectorTileEncoder
import scala.collection.JavaConversions._
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.WrappedResultSet
import scalikejdbc.DBSession
import scalikejdbc.ReadOnlyAutoSession
import scala.collection.Iterable
import scalikejdbc.DB
import concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.collection.AbstractIterator

object VectorTileStreamer extends LazyLogging {
  val extent = 256
  
  val zoomStart = 9
  val zoomEnd = 9
  
  // 6 ~ 1:200 000
  // 7 ~ 1:100 000
  // 8 ~ 1:50 000 ~ res 16
  // 9 ~ 1:25 000

  val res = Array(4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5)
  val sweref99tmbbox = new BoundingBox(218128, 6126002, 1083427, 7692850)
  val gridSet = GridSetFactory.createGridSet("sweref99tm", SRS.getSRS(3006), sweref99tmbbox, 
    false, res, null, null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, null, 256, 256, false)
  val reader = new ThreadLocal[WKBReader]() {
    override def initialValue(): WKBReader = new WKBReader()
  }
  
  class TRIt(it: TileRangeIterator) extends AbstractIterator[Array[Long]] {
    var hasNextState = true
    def next : Array[Long] = {
      val tileIndex = it.nextMetaGridLocation(Array.fill[Long](3)(0))
      if (tileIndex == null) hasNextState = false
      tileIndex
    }
    def hasNext : Boolean = hasNextState
  }

  def stream() {
    logger.info("Streaming starts...")
    val gridSubset = GridSubsetFactory.createGridSubSet(gridSet, sweref99tmbbox, zoomStart, zoomEnd)
    val tr = new TileRange("", "subset", zoomStart, zoomEnd, gridSubset.getCoverages, null, null, null)
    val it = new TileRangeIterator(tr, Array(1, 1))
    val iit = new TRIt(it)
    
    iit.grouped(100).foreach { x => x.par.foreach { x => streamTile(gridSubset, x) } }
    
    logger.info("Streaming ends...")
  }

  def tileIndexToString(tileIndex: Array[Long]): String = {
    val x = tileIndex(0)
    val y = tileIndex(1)
    val z = tileIndex(2)
    z + "/" + x + "/" + y
  }

  def toEnvelope(bbox: BoundingBox): Envelope = {
    new Envelope(bbox.getMinX, bbox.getMaxX, bbox.getMinY, bbox.getMaxY)
  }

  def parseWKB(bytes: Array[Byte]): Geometry = {
    var geometry: Geometry = null
    geometry = reader.get.read(bytes)
    geometry
  }

  def encodeRecord(name: String, 
      e: Envelope, 
      res: Double, 
      encoder: VectorTileEncoder, 
      x: WrappedResultSet) {
	  val kkod = x.int(1)
	  val geom = reader.get.read(x.bytes(2))
	  
    geom.apply(new CoordinateFilter() {
      override def filter(c: Coordinate) {
        c.x = (c.x - e.getMinX) / res
        c.y = extent - ((c.y - e.getMinY) / res) - 1
      }
    })
    val simplifiedGeom = DouglasPeuckerSimplifier.simplify(geom, 0.5)
    val attributes = x.toMap()
    val attributesNoGeom = attributes - "geom"
    encoder.addFeature(name, attributesNoGeom, simplifiedGeom)
  }

  def streamTile(gridSubset: GridSubset, tileIndex: Array[Long]) {
    if (tileIndex == null) return
    val tileBounds = gridSubset.boundsFromIndex(tileIndex)
    val envelope = toEnvelope(tileBounds)
    val z = tileIndex(2)
    val resolution = res(z.toInt)
    val encoder = new VectorTileEncoder(512, 8, true)
    
    DB readOnly { implicit session =>
      
      val vl_riks_kkod_z7 = Array(336,5011,5022,5025,5029,5032,5033,5034,5044,5051,5061,5811,5822,5825,5829,5832,5833,5834,5832,5844,5851,5861)
      val vl_riks_kkod_z8 = vl_riks_kkod_z7 ++ Array(5056,5058,5071,5082,5091,5856,5858,5871,5882,5891)
      
      PostGIS.fetch("terrang.al_riks", tileBounds).foreach(x => encodeRecord("terrang.al_riks", envelope, resolution, encoder, x))
      
      if (z < 7) PostGIS.fetch("sve1milj.vl_riks", tileBounds).foreach(x => encodeRecord("sve1milj.vl_riks", envelope, resolution, encoder, x))
      if (z < 7) PostGIS.fetch("sve1milj.tx_riks", tileBounds, "text,texttyp,thojd,trikt,tjust,tsparr,tkurv").foreach(x => encodeRecord("sve1milj.tx_riks", envelope, resolution, encoder, x))
      if (z < 7) PostGIS.fetch("sve1milj.jl_riks", tileBounds).foreach(x => encodeRecord("sve1milj.jl_riks", envelope, resolution, encoder, x))
      if (z < 7) PostGIS.fetch("sve1milj.mb_riks", tileBounds, "namn,bef").foreach(x => encodeRecord("sve1milj.mb_riks", envelope, resolution, encoder, x))
      if (z < 7) PostGIS.fetch("sve1milj.ml_riks", tileBounds).foreach(x => encodeRecord("sve1milj.ml_riks", envelope, resolution, encoder, x))
      if (z < 7) PostGIS.fetch("sve1milj.my_riks", tileBounds).foreach(x => encodeRecord("sve1milj.my_riks", envelope, resolution, encoder, x))
      
      if (z == 7) PostGIS.fetch("terrang.vl_riks", tileBounds, "", vl_riks_kkod_z7).foreach(x => encodeRecord("terrang.vl_riks", envelope, resolution, encoder, x))
      if (z > 7) PostGIS.fetch("terrang.vl_riks", tileBounds, "", vl_riks_kkod_z8).foreach(x => encodeRecord("terrang.vl_riks", envelope, resolution, encoder, x))
  	  if (z > 6) PostGIS.fetch("terrang.tx_riks", tileBounds, "text,texttyp,trikt,tjust,tsparr").foreach(x => encodeRecord("terrang.tx_riks", envelope, resolution, encoder, x))
  		if (z > 6) PostGIS.fetch("terrang.by_riks", tileBounds).foreach(x => encodeRecord("terrang.by_riks", envelope, resolution, encoder, x))
  		if (z > 6) PostGIS.fetch("terrang.my_riks", tileBounds).foreach(x => encodeRecord("terrang.my_riks", envelope, resolution, encoder, x))
  		if (z > 6) PostGIS.fetch("terrang.ml_riks", tileBounds).foreach(x => encodeRecord("terrang.ml_riks", envelope, resolution, encoder, x))
  		if (z > 7) PostGIS.fetch("terrang.oh_riks", tileBounds).foreach(x => encodeRecord("terrang.oh_riks", envelope, resolution, encoder, x))
    }
		
    val mbtile = encoder.encode()
    val path = "terrang/" + tileIndexToString(tileIndex) + ".pbf"
    Files.createDirectories(Paths.get(path).getParent)
    val file = new File(path)
    val fos = new FileOutputStream(file)
    fos.getChannel.write(ByteBuffer.wrap(mbtile))
  }
}
