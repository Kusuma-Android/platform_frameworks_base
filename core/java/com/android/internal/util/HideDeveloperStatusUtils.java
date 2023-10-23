/*
 * Copyright (C) 2023 TheParasiteProject
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

package com.android.internal.util;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.provider.Settings;

public class HideDeveloperStatusUtils {
    private static Set<String> mApps = new HashSet<>();
    private static final Set<String> settingsToHide = new HashSet<>(Arrays.asList(
        Settings.Global.ADB_ENABLED,
        Settings.Global.ADB_WIFI_ENABLED,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    ));

    public static boolean shouldHideDevStatus(ContentResolver cr, String packageName, String name) {
        return getApps(cr).contains(packageName) && settingsToHide.contains(name);
    }

    private static Set<String> getApps(ContentResolver cr) {
        String apps = Settings.Secure.getString(cr, Settings.Secure.HIDE_DEVELOPER_STATUS);
        if (apps != null) {
            mApps = new HashSet<>(Arrays.asList(apps.split(",")));
        } else {
            mApps = new HashSet<>();
        }
        return mApps;
    }

    public void addApp(Context mContext, String packageName, int userId) {
        mApps.add(packageName);
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", mApps), userId);
    }

    public void removeApp(Context mContext, String packageName, int userId) {
        mApps.remove(packageName);
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", mApps), userId);
    }

    public void setApps(Context mContext, int userId) {
        String apps = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, userId);
        if (apps != null) {
            mApps = new HashSet<>(Arrays.asList(apps.split(",")));
        } else {
            mApps = new HashSet<>();
        }
    }
}
