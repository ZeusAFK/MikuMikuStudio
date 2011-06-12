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
package com.jme3.scene.plugins.blender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.BlenderKey;
import com.jme3.asset.BlenderKey.FeaturesToLoad;
import com.jme3.asset.BlenderKey.LoadingResults;
import com.jme3.asset.BlenderKey.WorldData;
import com.jme3.asset.ModelKey;
import com.jme3.light.Light;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.plugins.blender.data.FileBlockHeader;
import com.jme3.scene.plugins.blender.data.Structure;
import com.jme3.scene.plugins.blender.exception.BlenderFileException;
import com.jme3.scene.plugins.blender.helpers.ArmatureHelper;
import com.jme3.scene.plugins.blender.helpers.CameraHelper;
import com.jme3.scene.plugins.blender.helpers.ConstraintHelper;
import com.jme3.scene.plugins.blender.helpers.CurvesHelper;
import com.jme3.scene.plugins.blender.helpers.IpoHelper;
import com.jme3.scene.plugins.blender.helpers.LightHelper;
import com.jme3.scene.plugins.blender.helpers.MaterialHelper;
import com.jme3.scene.plugins.blender.helpers.MeshHelper;
import com.jme3.scene.plugins.blender.helpers.ModifierHelper;
import com.jme3.scene.plugins.blender.helpers.NoiseHelper;
import com.jme3.scene.plugins.blender.helpers.ObjectHelper;
import com.jme3.scene.plugins.blender.helpers.ParticlesHelper;
import com.jme3.scene.plugins.blender.helpers.TextureHelper;
import com.jme3.scene.plugins.blender.utils.BlenderInputStream;
import com.jme3.scene.plugins.blender.utils.DataRepository;
import com.jme3.scene.plugins.blender.utils.JmeConverter;

/**
 * This is the main loading class. Have in notice that asset manager needs to have loaders for resources like textures.
 * @author Marcin Roguski
 */
public class BlenderLoader implements AssetLoader {

    private static final Logger LOGGER = Logger.getLogger(BlenderLoader.class.getName());

    @Override
    public LoadingResults load(AssetInfo assetInfo) throws IOException {
        try {
            //registering loaders
            ModelKey modelKey = (ModelKey) assetInfo.getKey();
            BlenderKey blenderKey;
            if (modelKey instanceof BlenderKey) {
                blenderKey = (BlenderKey) modelKey;
            } else {
                blenderKey = new BlenderKey(modelKey.getName());
                blenderKey.setAssetRootPath(modelKey.getFolder());
            }

            //opening stream
            BlenderInputStream inputStream = new BlenderInputStream(assetInfo.openStream(), assetInfo.getManager());

            //reading blocks
            List<FileBlockHeader> blocks = new ArrayList<FileBlockHeader>();
            FileBlockHeader fileBlock;
            DataRepository dataRepository = new DataRepository();
            dataRepository.setAssetManager(assetInfo.getManager());
            dataRepository.setInputStream(inputStream);
            dataRepository.setBlenderKey(blenderKey);

            //creating helpers
            dataRepository.putHelper(ArmatureHelper.class, new ArmatureHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(TextureHelper.class, new TextureHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(MeshHelper.class, new MeshHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(ObjectHelper.class, new ObjectHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(CurvesHelper.class, new CurvesHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(LightHelper.class, new LightHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(CameraHelper.class, new CameraHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(ModifierHelper.class, new ModifierHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(MaterialHelper.class, new MaterialHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(ConstraintHelper.class, new ConstraintHelper(inputStream.getVersionNumber(), dataRepository));
            dataRepository.putHelper(IpoHelper.class, new IpoHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(NoiseHelper.class, new NoiseHelper(inputStream.getVersionNumber()));
            dataRepository.putHelper(ParticlesHelper.class, new ParticlesHelper(inputStream.getVersionNumber()));

            //setting additional data to helpers
            if (blenderKey.isFixUpAxis()) {
                ObjectHelper objectHelper = dataRepository.getHelper(ObjectHelper.class);
                objectHelper.setyIsUpAxis(true);
                CurvesHelper curvesHelper = dataRepository.getHelper(CurvesHelper.class);
                curvesHelper.setyIsUpAxis(true);
            }
            MaterialHelper materialHelper = dataRepository.getHelper(MaterialHelper.class);
            materialHelper.setFaceCullMode(blenderKey.getFaceCullMode());

            //reading the blocks (dna block is automatically saved in the data repository when found)//TODO: zmienić to
            do {
                fileBlock = new FileBlockHeader(inputStream, dataRepository);
                if (!fileBlock.isDnaBlock()) {
                    blocks.add(fileBlock);
                }
            } while (!fileBlock.isLastBlock());

            JmeConverter converter = new JmeConverter(dataRepository);
            LoadingResults loadingResults = blenderKey.prepareLoadingResults();
            WorldData worldData = null;//a set of data used in different scene aspects
            for (FileBlockHeader block : blocks) {
                switch (block.getCode()) {
                    case FileBlockHeader.BLOCK_OB00://Object
                        Object object = converter.toObject(block.getStructure(dataRepository));
                        if (object instanceof Node) {
                            if ((blenderKey.getFeaturesToLoad() & FeaturesToLoad.OBJECTS) != 0) {
                                LOGGER.log(Level.INFO, "{0}: {1}--> {2}", new Object[]{((Node) object).getName(), ((Node) object).getLocalTranslation().toString(), ((Node) object).getParent() == null ? "null" : ((Node) object).getParent().getName()});
                                if (((Node) object).getParent() == null) {
                                    loadingResults.addObject((Node) object);
                                }
                            }
                        } else if (object instanceof Camera) {
                            if ((blenderKey.getFeaturesToLoad() & FeaturesToLoad.CAMERAS) != 0) {
                                loadingResults.addCamera((Camera) object);
                            }
                        } else if (object instanceof Light) {
                            if ((blenderKey.getFeaturesToLoad() & FeaturesToLoad.LIGHTS) != 0) {
                                loadingResults.addLight((Light) object);
                            }
                        }
                        break;
                    case FileBlockHeader.BLOCK_MA00://Material
                        if ((blenderKey.getFeaturesToLoad() & FeaturesToLoad.MATERIALS) != 0) {
                            loadingResults.addMaterial(converter.toMaterial(block.getStructure(dataRepository)));
                        }
                        break;
                    case FileBlockHeader.BLOCK_SC00://Scene
                        if ((blenderKey.getFeaturesToLoad() & FeaturesToLoad.SCENES) != 0) {
                            loadingResults.addScene(converter.toScene(block.getStructure(dataRepository)));
                        }
                        break;
                    case FileBlockHeader.BLOCK_WO00://World
                        if (worldData == null) {//onlu one world data is used
                            Structure worldStructure = block.getStructure(dataRepository);
                            String worldName = worldStructure.getName();
                            if (blenderKey.getUsedWorld() == null || blenderKey.getUsedWorld().equals(worldName)) {
                                worldData = converter.toWorldData(worldStructure);
                                if ((blenderKey.getFeaturesToLoad() & FeaturesToLoad.LIGHTS) != 0) {
                                    loadingResults.addLight(worldData.getAmbientLight());
                                }
                            }
                        }
                        break;
                }
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
            return loadingResults;
        } catch (BlenderFileException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }
}