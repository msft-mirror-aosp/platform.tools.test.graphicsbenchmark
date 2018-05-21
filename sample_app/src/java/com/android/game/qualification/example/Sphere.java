/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.game.qualification.example;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Sphere {
    private static final String VERTEX_SHADER_CODE =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    // the matrix must be included as a modifier of gl_Position
                    // Note that the uMVPMatrix factor *must be first* in order
                    // for the matrix multiplication product to be correct.
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";
    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";
    // number of coordinates per vertex in this array
    private static final int COORDS_PER_VERTEX = 3;
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private FloatBuffer mSphereVertices;
    private IntBuffer mIndices;
    private final int mProgram;

    private int mPositionHandle;
    private int mColorHandle;
    private int mMVPMatrixHandle;

    private double mRadius;
    private int mNumSegments;
    private int mPoints;
    private int mNumIndices;
    private float color[] = { 0.63671875f, 0.76953125f, 0.22265625f, 0.0f };

    public Sphere( float radius, int numSegments) {
        this.mRadius = radius;
        this.mNumSegments = numSegments;
        mPoints = build();

        // prepare shaders and OpenGL program
        int vertexShader = MyGLRenderer.loadShader(
                GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = MyGLRenderer.loadShader(
                GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);
        mProgram = GLES20.glCreateProgram();             // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);                  // create OpenGL program executables
    }

    public double getRadius() {
        return mRadius;
    }

    public void draw(float[] mvpMatrix) {
        // Add program to OpenGL environment
        GLES20.glUseProgram(mProgram);
        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(
                mPositionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE,
                mSphereVertices);
        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
        // Set color for drawing the triangle
        GLES20.glUniform4fv(mColorHandle, 1, color, 0);
        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        MyGLRenderer.checkGlError("glGetUniformLocation");
        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        MyGLRenderer.checkGlError("glUniformMatrix4fv");
        // Draw the sphere.
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLE_STRIP, mNumIndices, GLES20.GL_UNSIGNED_INT, mIndices);
        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);

    }

    private int build() {
        // initialize vertex byte buffer for shape coordinates
        mSphereVertices =
                ByteBuffer.allocateDirect(
                        mNumSegments * (mNumSegments + 1) * COORDS_PER_VERTEX * VERTEX_STRIDE * 2)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        mIndices =
                ByteBuffer.allocateDirect(mNumSegments * (mNumSegments + 2) * 2 * 4)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer();
        /*
         * x = r * sin(phi) * cos(theta)
         * y = r * sin(phi) * sin(theta)
         * z = r * cos(phi)
         */
        double dTheta = 2 * Math.PI / mNumSegments;
        double dPhi = Math.PI / mNumSegments;
        int points = 0;
        boolean firstLoop = true;

        double epsilon = 1e-10;

        for(double phi = -(Math.PI); phi <= 0 + epsilon; phi += dPhi) {
            //for each stage calculating the slices
            for(double theta = 0.0; theta < (Math.PI * 2) - epsilon; theta+=dTheta) {
                mSphereVertices.put((float) (mRadius * Math.sin(phi) * Math.cos(theta)) );
                mSphereVertices.put((float) (mRadius * Math.sin(phi) * Math.sin(theta)) );
                mSphereVertices.put((float) (mRadius * Math.cos(phi)) );

                if (!firstLoop) {
                    mIndices.put(points - mNumSegments);
                    mIndices.put(points);
                    mNumIndices += 2;
                }
                points++;

            }
            if (!firstLoop) {
                // Finish off layer
                mIndices.put(points - 2 * mNumSegments);
                mIndices.put(points - mNumSegments);
                mNumIndices += 2;
            }
            firstLoop = false;
        }
        mIndices.position(0);
        mSphereVertices.position(0);

        return points;
    }
}
