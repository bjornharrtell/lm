package org.wololo.postgis2tiles2

import org.wololo.postgis2tiles2.Coordinate2
import org.wololo.postgis2tiles2.Extent

case class TileGrid(origin: Coordinate2 = Coordinate2(0,0), tileSize: Int = 256, maxRes: Int = 4096, levels: Int = 10) {
  def resolution(level: Int) = (maxRes / math.pow(2, level)).toInt
  
  def tileExtent(x: Int, y: Int, z: Int) : Extent = {
    val w = tileSize * resolution(z)
    val minx = origin.x + w * x
    val miny = origin.y + w * y
    Extent(minx, miny, minx + w, miny + w)
  }
  
  
}