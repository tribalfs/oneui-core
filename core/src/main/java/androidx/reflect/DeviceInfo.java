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

package androidx.reflect;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;


public class DeviceInfo {
    @SuppressLint("NewApi")
    private static String getSystemProp(@Nullable String key) {
        String value = null;
        try {
            value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
        } catch (IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException | ClassNotFoundException e) {
            value = "Unknown";
        }
        return value;
    }

    private static Boolean isSammy = null;

    @SuppressLint("NewApi")
    public static boolean isSamsung() {
        if (isSammy == null){
            isSammy =  Build.MANUFACTURER.toLowerCase(Locale.ENGLISH).equals("samsung");
        }
        return isSammy;
    }
}
