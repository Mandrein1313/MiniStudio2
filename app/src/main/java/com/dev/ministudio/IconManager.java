package com.dev.ministudio;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class IconManager {

    // ฟังก์ชันนี้จะ "สแกน" หาไฟล์ใน drawable ที่ขึ้นต้นด้วย "ic_"
    public static List<Integer> getAllIconIds(Context context) {
        List<Integer> iconIds = new ArrayList<>();
        Field[] drawables = R.drawable.class.getFields();

        for (Field f : drawables) {
            try {
                // เช็คว่าชื่อไฟล์ขึ้นต้นด้วย "ic_" หรือไม่
                if (f.getName().startsWith("ic_")) {
                    int resId = f.getInt(null);
                    iconIds.add(resId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return iconIds;
    }

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SELECTED_ICON = "selected_icon";

    public static void saveSelectedIcon(Context context, int resId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit().putInt(KEY_SELECTED_ICON, resId).apply();
    }
}
