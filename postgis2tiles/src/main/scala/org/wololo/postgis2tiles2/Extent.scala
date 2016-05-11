package org.wololo.postgis2tiles2

case class Extent(minx: Int, miny: Int, maxx: Int, maxy: Int) {
  def width = maxx - minx
  def height = maxy - miny
}