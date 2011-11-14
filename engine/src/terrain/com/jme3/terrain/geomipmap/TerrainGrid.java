/*
 * Copyright (c) 2009-2010 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.terrain.geomipmap;

import com.jme3.scene.control.UpdateControl;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.terrain.heightmap.HeightMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.lodcalc.LodCalculator;
import com.jme3.terrain.heightmap.HeightMapGrid;
import java.util.concurrent.Callable;

/**
 * 
 * The grid is indexed by cells. Each cell has an integer XZ coordinate originating at 0,0.
 * TerrainGrid will piggyback on the TerrainLodControl so it can use the camera for its
 * updates as well. It does this in the overwritten update() method.
 * 
 * @author Anthyon
 */
public class TerrainGrid extends TerrainQuad {

    protected static final Logger log = Logger.getLogger(TerrainGrid.class.getCanonicalName());
    protected Vector3f currentCamCell;
    protected int quarterSize;
    protected int quadSize;
    protected HeightMapGrid heightMapGrid;
    protected Vector3f[] quadIndex;
    protected Map<String, TerrainGridListener> listeners = new HashMap<String, TerrainGridListener>();
    protected Material material;
    protected LRUCache<Vector3f, TerrainQuad> cache = new LRUCache<Vector3f, TerrainQuad>(16);
    protected RigidBodyControl[] quadControls;
    protected PhysicsSpace space;
    private int cellsLoaded = 0;
    private int[] gridOffset;

    protected class UpdateQuadCache implements Runnable {

        protected final Vector3f location;

        public UpdateQuadCache(Vector3f location) {
            this.location = location;
        }

        public void run() {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int quadIdx = i * 4 + j;
                    final Vector3f temp = location.add(quadIndex[quadIdx]);
                    TerrainQuad q = cache.get(temp);
                    if (q == null) {
                        // create the new Quad since it doesn't exist
                        HeightMap heightMapAt = heightMapGrid.getHeightMapAt(temp);
                        q = new TerrainQuad(getName() + "Quad" + temp, patchSize, quadSize, heightMapAt == null ? null : heightMapAt.getHeightMap());
                        q.setMaterial(material.clone());
                        log.log(Level.FINE, "Loaded TerrainQuad {0}", q.getName());
                    }
                    cache.put(temp, q);

                    if (isCenter(quadIdx)) {
                        // if it should be attached as a child right now, attach it
                        final int quadrant = getQuadrant(quadIdx);
                        final TerrainQuad newQuad = q;
                        // back on the OpenGL thread:
                        getControl(UpdateControl.class).enqueue(new Callable() {

                            public Object call() throws Exception {
                                attachQuadAt(newQuad, quadrant, temp);
                                newQuad.resetCachedNeighbours();
                                return null;
                            }
                        });
                    }
                }
            }

        }
    }

    protected boolean isCenter(int quadIndex) {
        return quadIndex == 9 || quadIndex == 5 || quadIndex == 10 || quadIndex == 6;
    }

    protected int getQuadrant(int quadIndex) {
        if (quadIndex == 5) {
            return 1;
        } else if (quadIndex == 9) {
            return 2;
        } else if (quadIndex == 6) {
            return 3;
        } else if (quadIndex == 10) {
            return 4;
        }
        return 0; // error
    }

    public TerrainGrid(String name, int patchSize, int maxVisibleSize, Vector3f scale, HeightMapGrid heightMapGrid,
            Vector2f offset, float offsetAmount) {
        this.name = name;
        this.patchSize = patchSize;
        this.size = maxVisibleSize;
        this.quarterSize = maxVisibleSize >> 2;
        this.quadSize = (maxVisibleSize + 1) >> 1;
        this.stepScale = scale;
        this.heightMapGrid = heightMapGrid;
        heightMapGrid.setSize(this.quadSize);
        this.totalSize = maxVisibleSize;
        this.offset = offset;
        this.offsetAmount = offsetAmount;
        this.gridOffset = new int[]{0,0};
        
        /*
         *        -z
         *         | 
         *        1|3 
         *  -x ----+---- x
         *        2|4
         *         |
         *         z
         */
        this.quadIndex = new Vector3f[]{
        new Vector3f(-1, 0, -1), new Vector3f(0, 0, -1), new Vector3f(1, 0, -1), new Vector3f(2, 0, -1),
        new Vector3f(-1, 0, 0), new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(2, 0, 0),
        new Vector3f(-1, 0, 1), new Vector3f(0, 0, 1), new Vector3f(1, 0, 1), new Vector3f(2, 0, 1),
        new Vector3f(-1, 0, 2), new Vector3f(0, 0, 2), new Vector3f(1, 0, 2), new Vector3f(2, 0, 2)};

        addControl(new UpdateControl());
    }

    public TerrainGrid(String name, int patchSize, int maxVisibleSize, Vector3f scale, HeightMapGrid heightMapGrid) {
        this(name, patchSize, maxVisibleSize, scale, heightMapGrid, new Vector2f(), 0);
    }

    public TerrainGrid(String name, int patchSize, int maxVisibleSize, HeightMapGrid heightMapGrid) {
        this(name, patchSize, maxVisibleSize, Vector3f.UNIT_XYZ, heightMapGrid);
    }

    public TerrainGrid() {
    }

    public void initialize(Vector3f location) {
        if (this.material == null) {
            throw new RuntimeException("Material must be set prior to call of initialize");
        }
        Vector3f camCell = this.getCamCell(location);
        this.updateChildrens(camCell);
        for (TerrainGridListener l : this.listeners.values()) {
            l.gridMoved(camCell);
        }
    }

    @Override
    public void update(List<Vector3f> locations, LodCalculator lodCalculator) {
        // for now, only the first camera is handled.
        // to accept more, there are two ways:
        // 1: every camera has an associated grid, then the location is not enough to identify which camera location has changed
        // 2: grids are associated with locations, and no incremental update is done, we load new grids for new locations, and unload those that are not needed anymore
        Vector3f cam = locations.get(0);
        Vector3f camCell = this.getCamCell(cam); // get the grid index value of where the camera is (ie. 2,1)
        if(cellsLoaded>1){                  // Check if cells are updated before updating gridoffset.
            gridOffset[0] = Math.round(camCell.x*(size/2));
            gridOffset[1] = Math.round(camCell.z*(size/2));
            cellsLoaded=0;
        }
        if (camCell.x != this.currentCamCell.x || camCell.z != currentCamCell.z) {
            // if the camera has moved into a new cell, load new terrain into the visible 4 center quads
            this.updateChildrens(camCell);
            for (TerrainGridListener l : this.listeners.values()) {
                l.gridMoved(camCell);
            }
        }
        super.update(locations, lodCalculator);
    }

    public Vector3f getCamCell(Vector3f location) {
        Vector3f tile = getTileCell(location);
        Vector3f offsetHalf = new Vector3f(-0.5f, 0, -0.5f);
        Vector3f shifted = tile.subtract(offsetHalf);
        return new Vector3f(FastMath.floor(shifted.x), 0, FastMath.floor(shifted.z));
    }
    
    /**
     * Centered at 0,0.
     * Get the tile index location in integer form:
     * @param location world coordinate
     */
    public Vector3f getTileCell(Vector3f location) {
        Vector3f tileLoc = location.divide(this.getWorldScale().mult(this.quadSize));
        return tileLoc;
    }

    protected void removeQuad(int idx) {
        if (this.getQuad(idx) != null) {
            if (quadControls != null) {
                this.getQuad(idx).removeControl(RigidBodyControl.class);
            }
            for (TerrainGridListener l : listeners.values()) {
                l.tileDetached(getTileCell(this.getQuad(idx).getWorldTranslation()), this.getQuad(idx));
            }
            this.detachChild(this.getQuad(idx));
            cellsLoaded++; // For gridoffset calc., maybe the run() method is a better location for this.
        }
    }

    /**
     * Runs on the rendering thread
     */
    protected void attachQuadAt(TerrainQuad q, int quadrant, Vector3f cam) {
        this.removeQuad(quadrant);
        
        q.setQuadrant((short) quadrant);
        this.attachChild(q);

        Vector3f loc = cam.mult(this.quadSize - 1).subtract(quarterSize, 0, quarterSize);// quadrant location handled TerrainQuad automatically now
        q.setLocalTranslation(loc);

        for (TerrainGridListener l : listeners.values()) {
            l.tileAttached(cam, q);
        }
        updateModelBound();
    }

    /**
     * Called when the camera has moved into a new cell. We need to
     * update what quads are in the scene now.
     * 
     * Step 1: touch cache
     * LRU cache is used, so elements that need to remain
     * should be touched.
     *
     * Step 2: load new quads in background thread
     * if the camera has moved into a new cell, we load in new quads
     * @param camCell the cell the camera is in
     */
    protected void updateChildrens(Vector3f camCell) {
        
        int dx = 0;
        int dy = 0;
        if (currentCamCell != null) {
            dx = (int) (camCell.x - currentCamCell.x);
            dy = (int) (camCell.z - currentCamCell.z);
        }

        int xMin = 0;
        int xMax = 4;
        int yMin = 0;
        int yMax = 4;
        if (dx == -1) { // camera moved to -X direction
            xMax = 3;
        } else if (dx == 1) { // camera moved to +X direction
            xMin = 1;
        }

        if (dy == -1) { // camera moved to -Y direction
            yMax = 3;
        } else if (dy == 1) { // camera moved to +Y direction
            yMin = 1;
        }

        // Touch the items in the cache that we are and will be interested in.
        // We activate cells in the direction we are moving. If we didn't move 
        // either way in one of the axes (say X or Y axis) then they are all touched.
        for (int i = yMin; i < yMax; i++) {
            for (int j = xMin; j < xMax; j++) {
                cache.get(camCell.add(quadIndex[i * 4 + j]));
            }
        }
        // ---------------------------------------------------
        // ---------------------------------------------------

        if (executor == null) {
            // use the same executor as the LODControl
            executor = createExecutorService();
        }

        executor.submit(new UpdateQuadCache(camCell));

        this.currentCamCell = camCell;
    }

    public void addListener(String id, TerrainGridListener listener) {
        this.listeners.put(id, listener);
    }

    public Vector3f getCurrentCell() {
        return this.currentCamCell;
    }

    public void removeListener(String id) {
        this.listeners.remove(id);
    }

    @Override
    public void setMaterial(Material mat) {
        this.material = mat;
        super.setMaterial(mat);
    }

    public void setQuadSize(int quadSize) {
        this.quadSize = quadSize;
    }

    @Override
    public void adjustHeight(List<Vector2f> xz, List<Float> height) {
        Vector3f currentGridLocation = getCurrentCell().mult(getLocalScale()).multLocal(quadSize - 1);
        for (Vector2f vect : xz) {
            vect.x -= currentGridLocation.x;
            vect.y -= currentGridLocation.z;
        }
        super.adjustHeight(xz, height);
    }
    
    @Override
    protected float getHeightmapHeight(int x, int z) {
        return super.getHeightmapHeight(x-gridOffset[0], z-gridOffset[1]);
    }

}
