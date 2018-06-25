/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.game.qualification.example;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.util.Random;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "MyGLRenderer";
    private Sphere[] mSphere;
    private int numSpheres = 1;
    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];

    private float[][] mPosition;
    private float[][] mVelocity;
    private float mHeight;
    private float mWidth;


    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        Random rand = new Random(5);

        mSphere = new Sphere[numSpheres];
        mVelocity = new float[numSpheres][3];
        mPosition = new float[numSpheres][3];

        for (int i = 0; i < mSphere.length; ++i) {
            mSphere[i] = new Sphere(0.005f, 75);

            mVelocity[i][0] = (rand.nextFloat() * 2 - 1) * 0.02f;
            mVelocity[i][1] = (rand.nextFloat() * 2 - 1) * 0.02f;
            mVelocity[i][2] = 0.0f;
        }
    }

    public void onDrawFrame(GL10 unused) {
        float[] scratch = new float[16];

        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 10, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        for (int i = 0; i < mSphere.length; i++) {
            // Update position;
            mPosition[i][0] += mVelocity[i][0];
            mPosition[i][1] += mVelocity[i][1];
            mPosition[i][2] += mVelocity[i][2];
            if (mPosition[i][0] - mSphere[i].getRadius() < 0.0f
                    || mPosition[i][0] + mSphere[i].getRadius() > mWidth) {
                mVelocity[i][0] = -mVelocity[i][0];
            }
            if (mPosition[i][1] - mSphere[i].getRadius() < 0.0f
                    || mPosition[i][1] + mSphere[i].getRadius() > mHeight) {
                mVelocity[i][1] = -mVelocity[i][1];
            }

            Matrix.translateM(scratch, 0, mMVPMatrix, 0, mPosition[i][0], mPosition[i][1], mPosition[i][2]);

            mSphere[i].draw(scratch);
        }
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;
        mHeight = 1;
        mWidth = ratio;
        for (int i = 0; i < mSphere.length; i++) {
            mPosition[i][0] = mWidth / 2.0f;
            mPosition[i][1] = mHeight / 2.0f;
        }

        // This projection matrix is applied to object coordinates in the onDrawFrame() method.
        // Using orthographic projection to make it easier to determine edge of screen.
        Matrix.orthoM(mProjectionMatrix, 0, 0, ratio, 0, 1, 3, 20);
    }
        /**
     * Utility method for compiling a OpenGL shader.
     *
     * <p><strong>Note:</strong> When developing shaders, use the checkGlError()
     * method to debug shader coding errors.</p>
     *
     * @param type - Vertex or fragment shader type.
     * @param shaderCode - String containing the shader code.
     * @return - Returns an id for the shader.
     */
    public static int loadShader(int type, String shaderCode){
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);
        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
    /**
    * Utility method for debugging OpenGL calls. Provide the name of the call
    * just after making it:
    *
    * <pre>
    * mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
    * MyGLRenderer.checkGlError("glGetUniformLocation");</pre>
    *
    * If the operation is not successful, the check throws an error.
    *
    * @param glOperation - Name of the OpenGL call to check.
    */
    public static void checkGlError(String glOperation) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }

}
