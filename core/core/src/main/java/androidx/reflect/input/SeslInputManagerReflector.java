package androidx.reflect.input;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.hardware.input.InputManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.reflect.SeslBaseReflector;

import java.lang.reflect.Method;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY_GROUP_PREFIX)
public class SeslInputManagerReflector {
    @RequiresApi(21)
    private static final Class<?> mClass = InputManager.class;

    private SeslInputManagerReflector() {
    }

    @RequiresApi(21)
    private static Object getInstance() {
        Method method = SeslBaseReflector.getMethod(mClass, "getInstance");
        if (method != null) {
            return SeslBaseReflector.invoke(null, method);
        }

        return null;
    }

    public static void setPointerIconType(int iconId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Object inputManager = getInstance();
            if (inputManager != null) {
                Method method = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    method = SeslBaseReflector.getDeclaredMethod(mClass, "hidden_setPointerIconType", Integer.TYPE);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    method = SeslBaseReflector.getMethod(mClass, "setPointerIconType", Integer.TYPE);
                }

                if (method != null) {
                    SeslBaseReflector.invoke(inputManager, method, iconId);
                }
            }
        }
    }
}
