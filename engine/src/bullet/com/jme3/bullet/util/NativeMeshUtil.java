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
package com.jme3.bullet.util;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 *
 * @author normenhansen
 */
public class NativeMeshUtil {
    
    public static long getTriangleIndexVertexArray(Mesh mesh){
        ByteBuffer triangleIndexBase = ByteBuffer.allocateDirect(mesh.getTriangleCount() * 3 * 4).order(ByteOrder.nativeOrder());
        ByteBuffer vertexBase = ByteBuffer.allocateDirect(mesh.getVertexCount() * 3 * 4).order(ByteOrder.nativeOrder());
        int numVertices = mesh.getVertexCount();
        int vertexStride = 12; //3 verts * 4 bytes per.
        int numTriangles = mesh.getTriangleCount();
        int triangleIndexStride = 12; //3 index entries * 4 bytes each.

        IndexBuffer indices = mesh.getIndexBuffer();
        FloatBuffer vertices = mesh.getFloatBuffer(Type.Position);
        vertices.rewind();

        int verticesLength = mesh.getVertexCount() * 3;
        for (int i = 0; i < verticesLength; i++) {
            float tempFloat = vertices.get();
            vertexBase.putFloat(tempFloat);
        }

        int indicesLength = mesh.getTriangleCount() * 3;
        for (int i = 0; i < indicesLength; i++) {
            triangleIndexBase.putInt(indices.get(i));
        }
        vertices.rewind();
        vertices.clear();

        return createTriangleIndexVertexArray(triangleIndexBase, vertexBase, numTriangles, numVertices, vertexStride, triangleIndexStride);
    }
    
    public static native long createTriangleIndexVertexArray(ByteBuffer triangleIndexBase, ByteBuffer vertexBase, int numTraingles, int numVertices, int vertextStride, int triangleIndexStride);
    
}