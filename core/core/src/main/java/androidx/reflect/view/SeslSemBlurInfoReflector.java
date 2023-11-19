/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.reflect.view;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.reflect.DeviceInfo;
import androidx.reflect.SeslBaseReflector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY_GROUP_PREFIX)
public class SeslSemBlurInfoReflector {
    private static final String TAG = "SeslSemBlurInfoReflector";
    private static final String mBuilderClass = "android.view.SemBlurInfo$Builder";

    private SeslSemBlurInfoReflector() {
    }

    public static Object semCreateBlurBuilder(int blurMode) {
        if (DeviceInfo.isSamsung()) {
            Constructor<?> constructor = SeslBaseReflector.getConstructor(mBuilderClass, Integer.TYPE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (constructor != null) {
                    try {
                        return constructor.newInstance(blurMode);
                    } catch (IllegalAccessException e) {
                        Log.e(TAG, "semCreateBlurBuilder IllegalAccessException", e);
                    } catch (InstantiationException e) {
                        Log.e(TAG, "semCreateBlurBuilder InstantiationException", e);
                    } catch (InvocationTargetException e) {
                        Log.e(TAG, "semCreateBlurBuilder InvocationTargetException", e);
                    }
                }
            }
        }

        return null;
    }

    public static Object semSetBuilderBlurRadius(Object builder, int radius) {
        if (DeviceInfo.isSamsung()) {
            Method method;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                method = SeslBaseReflector.getDeclaredMethod(mBuilderClass, "hidden_setRadius", Integer.TYPE);
            } else {
                method = null;
            }

            if (method != null) {
                method.setAccessible(true);
                SeslBaseReflector.invoke(builder, method, radius);
            }
        }
        return builder;
    }

    public static Object semSetBuilderBlurBackgroundColor(Object builder, int color) {
        if (DeviceInfo.isSamsung()) {
            Method method;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                method = SeslBaseReflector.getDeclaredMethod(mBuilderClass, "hidden_setBackgroundColor", Integer.TYPE);
            } else {
                method = null;
            }

            if (method != null) {
                method.setAccessible(true);
                SeslBaseReflector.invoke(builder, method, color);
            }
        }
        return builder;
    }

    public static Object semSetBuilderBlurBackgroundCornerRadius(Object builder, float cornerRadius) {
        if (DeviceInfo.isSamsung()) {
            Method method;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                method = SeslBaseReflector.getDeclaredMethod(mBuilderClass, "hidden_setBackgroundCornerRadius", Float.TYPE);
            } else {
                method = null;
            }

            if (method != null) {
                method.setAccessible(true);
                SeslBaseReflector.invoke(builder, method, cornerRadius);
            }
        }
        return builder;
    }

    public static void semBuildSetBlurInfo(Object builder, @NonNull View view) {
        if (DeviceInfo.isSamsung()) {
            Method method;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                method = SeslBaseReflector.getDeclaredMethod(mBuilderClass, "hidden_build");
            } else {
                method = null;
            }

            if (method != null) {
                method.setAccessible(true);
                SeslViewReflector.semSetBlurInfo(view, SeslBaseReflector.invoke(builder, method));
            }
        }
    }
}
