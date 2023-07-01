
package androidx.reflect;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

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
