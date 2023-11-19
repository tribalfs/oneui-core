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

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.reflect.DeviceInfo;
import androidx.reflect.SeslBaseReflector;

import java.lang.reflect.Method;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

public class SeslSemWindowManagerReflector {
    private static String mClassName = "com.samsung.android.view.SemWindowManager";
    private static String mListenerName = "com.samsung.android.view.SemWindowManager$FoldStateListener";

    private SeslSemWindowManagerReflector() {
    }

    private static Object getInstance() {
        Method method = SeslBaseReflector.getMethod(mClassName, "getInstance");
        if (method != null) {
            Object result = SeslBaseReflector.invoke(null, method);
            if (result.getClass().getName().equals(mClassName)) {
                return result;
            }
        }

        return null;
    }

    public static void registerFoldStateListener(@NonNull Object listener, Object handler) {
        if (DeviceInfo.isSamsung()) {
            Method method = SeslBaseReflector.getMethod(mClassName, "registerFoldStateListener", SeslBaseReflector.getClass(mListenerName), Handler.class);
            if (method != null) {
                SeslBaseReflector.invoke(getInstance(), method, listener, handler);
            }
        }
    }

    public static void unregisterFoldStateListener(@NonNull Object listener) {
        if (DeviceInfo.isSamsung()) {
            Method method = SeslBaseReflector.getMethod(mClassName, "unregisterFoldStateListener", SeslBaseReflector.getClass(mListenerName));
            if (method != null) {
                SeslBaseReflector.invoke(getInstance(), method, listener);
            }
        }
    }

    public static boolean isTableMode() {
        if (DeviceInfo.isSamsung()) {
            Method method = SeslBaseReflector.getMethod(mClassName, "isTableMode");
            if (method != null) {
                Object result = SeslBaseReflector.invoke(getInstance(), method);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        }

        return false;
    }
}
