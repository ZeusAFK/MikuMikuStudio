/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jme3.terrain.geomipmap;

/**
 * Used for TerrainQuad to find neighbours that are not part of the
 * same quad tree. Normally TerrainQuads function in a quad tree and
 * use the neighbour methods getRightQuad, getLeftQuad etc. to update
 * LOD values of the terrain (and for some other routines).
 * 
 * With this you can have a parent, control or spatial, that manages a group of
 * TerrainQuads by linking them together through these four methods.
 * 
 * The general orientation of TerrainQuads and their sun-quads is as such:
 * 
 * 
 *  +-- x+ ---->
 *  |
 *  |    1 | 3 (quadrants)
 *  z+   --+--
 *  |    2 | 4
 *  |
 * \/
 * 
 * Your implementation will still have to manage getHeight, getNormal, and 
 * most other Terrain.java interface methods; often by offsetting the XZ
 * coordinate parameters.
 * 
 * @author sploreg
 */
public interface NeighbourFinder {
    
    public TerrainQuad getRightQuad(TerrainQuad center);
    
    public TerrainQuad getLeftQuad(TerrainQuad center);
    
    public TerrainQuad getTopQuad(TerrainQuad center);
    
    public TerrainQuad getDownQuad(TerrainQuad center);
}
