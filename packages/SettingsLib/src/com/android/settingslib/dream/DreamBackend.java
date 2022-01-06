/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settingslib.dream;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DreamBackend {
    private static final String TAG = "DreamBackend";
    private static final boolean DEBUG = false;

    public static class DreamInfo {
        public CharSequence caption;
        public Drawable icon;
        public boolean isActive;
        public ComponentName componentName;
        public ComponentName settingsComponentName;
        public CharSequence description;
        public Drawable previewImage;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(DreamInfo.class.getSimpleName());
            sb.append('[').append(caption);
            if (isActive)
                sb.append(",active");
            sb.append(',').append(componentName);
            if (settingsComponentName != null)
                sb.append("settings=").append(settingsComponentName);
            return sb.append(']').toString();
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WHILE_CHARGING, WHILE_DOCKED, EITHER, NEVER})
    public @interface WhenToDream{}

    public static final int WHILE_CHARGING = 0;
    public static final int WHILE_DOCKED = 1;
    public static final int EITHER = 2;
    public static final int NEVER = 3;

    private final Context mContext;
    private final IDreamManager mDreamManager;
    private final DreamInfoComparator mComparator;
    private final boolean mDreamsEnabledByDefault;
    private final boolean mDreamsActivatedOnSleepByDefault;
    private final boolean mDreamsActivatedOnDockByDefault;

    private static DreamBackend sInstance;

    public static DreamBackend getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DreamBackend(context);
        }
        return sInstance;
    }

    public DreamBackend(Context context) {
        mContext = context.getApplicationContext();
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.getService(DreamService.DREAM_SERVICE));
        mComparator = new DreamInfoComparator(getDefaultDream());
        mDreamsEnabledByDefault = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_dreamsEnabledByDefault);
        mDreamsActivatedOnSleepByDefault = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault);
        mDreamsActivatedOnDockByDefault = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault);
    }

    public List<DreamInfo> getDreamInfos() {
        logd("getDreamInfos()");
        ComponentName activeDream = getActiveDream();
        PackageManager pm = mContext.getPackageManager();
        Intent dreamIntent = new Intent(DreamService.SERVICE_INTERFACE);
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(dreamIntent,
                PackageManager.GET_META_DATA);
        List<DreamInfo> dreamInfos = new ArrayList<>(resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.serviceInfo == null) {
                continue;
            }
            DreamInfo dreamInfo = new DreamInfo();
            dreamInfo.caption = resolveInfo.loadLabel(pm);
            dreamInfo.icon = resolveInfo.loadIcon(pm);
            dreamInfo.description = getDescription(resolveInfo, pm);
            dreamInfo.componentName = getDreamComponentName(resolveInfo);
            dreamInfo.isActive = dreamInfo.componentName.equals(activeDream);

            final DreamMetadata dreamMetadata = getDreamMetadata(pm, resolveInfo);
            if (dreamMetadata != null) {
                dreamInfo.settingsComponentName = dreamMetadata.mSettingsActivity;
                dreamInfo.previewImage = dreamMetadata.mPreviewImage;
            }

            dreamInfos.add(dreamInfo);
        }
        Collections.sort(dreamInfos, mComparator);
        return dreamInfos;
    }

    private static CharSequence getDescription(ResolveInfo resolveInfo, PackageManager pm) {
        String packageName = resolveInfo.resolvePackageName;
        ApplicationInfo applicationInfo = null;
        if (packageName == null) {
            packageName = resolveInfo.serviceInfo.packageName;
            applicationInfo = resolveInfo.serviceInfo.applicationInfo;
        }
        if (resolveInfo.serviceInfo.descriptionRes != 0) {
            return pm.getText(packageName,
                    resolveInfo.serviceInfo.descriptionRes,
                    applicationInfo);
        }
        return null;
    }

    public ComponentName getDefaultDream() {
        if (mDreamManager == null) {
            return null;
        }
        try {
            return mDreamManager.getDefaultDreamComponentForUser(mContext.getUserId());
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get default dream", e);
            return null;
        }
    }

    public CharSequence getActiveDreamName() {
        ComponentName cn = getActiveDream();
        if (cn != null) {
            PackageManager pm = mContext.getPackageManager();
            try {
                ServiceInfo ri = pm.getServiceInfo(cn, 0);
                if (ri != null) {
                    return ri.loadLabel(pm);
                }
            } catch (PackageManager.NameNotFoundException exc) {
                return null; // uninstalled?
            }
        }
        return null;
    }

    /**
     * Gets an icon from active dream.
     */
    public Drawable getActiveIcon() {
        final ComponentName cn = getActiveDream();
        if (cn != null) {
            final PackageManager pm = mContext.getPackageManager();
            try {
                final ServiceInfo ri = pm.getServiceInfo(cn, 0);
                if (ri != null) {
                    return ri.loadIcon(pm);
                }
            } catch (PackageManager.NameNotFoundException exc) {
                return null;
            }
        }
        return null;
    }

    public @WhenToDream int getWhenToDreamSetting() {
        if (!isEnabled()) {
            return NEVER;
        }
        return isActivatedOnDock() && isActivatedOnSleep() ? EITHER
                : isActivatedOnDock() ? WHILE_DOCKED
                : isActivatedOnSleep() ? WHILE_CHARGING
                : NEVER;
    }

    public void setWhenToDream(@WhenToDream int whenToDream) {
        setEnabled(whenToDream != NEVER);

        switch (whenToDream) {
            case WHILE_CHARGING:
                setActivatedOnDock(false);
                setActivatedOnSleep(true);
                break;

            case WHILE_DOCKED:
                setActivatedOnDock(true);
                setActivatedOnSleep(false);
                break;

            case EITHER:
                setActivatedOnDock(true);
                setActivatedOnSleep(true);
                break;

            case NEVER:
            default:
                break;
        }

    }

    public boolean isEnabled() {
        return getBoolean(Settings.Secure.SCREENSAVER_ENABLED, mDreamsEnabledByDefault);
    }

    public void setEnabled(boolean value) {
        logd("setEnabled(%s)", value);
        setBoolean(Settings.Secure.SCREENSAVER_ENABLED, value);
    }

    public boolean isActivatedOnDock() {
        return getBoolean(Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                mDreamsActivatedOnDockByDefault);
    }

    public void setActivatedOnDock(boolean value) {
        logd("setActivatedOnDock(%s)", value);
        setBoolean(Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK, value);
    }

    public boolean isActivatedOnSleep() {
        return getBoolean(Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                mDreamsActivatedOnSleepByDefault);
    }

    public void setActivatedOnSleep(boolean value) {
        logd("setActivatedOnSleep(%s)", value);
        setBoolean(Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, value);
    }

    private boolean getBoolean(String key, boolean def) {
        return Settings.Secure.getInt(mContext.getContentResolver(), key, def ? 1 : 0) == 1;
    }

    private void setBoolean(String key, boolean value) {
        Settings.Secure.putInt(mContext.getContentResolver(), key, value ? 1 : 0);
    }

    public void setActiveDream(ComponentName dream) {
        logd("setActiveDream(%s)", dream);
        if (mDreamManager == null)
            return;
        try {
            ComponentName[] dreams = { dream };
            mDreamManager.setDreamComponents(dream == null ? null : dreams);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set active dream to " + dream, e);
        }
    }

    public ComponentName getActiveDream() {
        if (mDreamManager == null)
            return null;
        try {
            ComponentName[] dreams = mDreamManager.getDreamComponents();
            return dreams != null && dreams.length > 0 ? dreams[0] : null;
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get active dream", e);
            return null;
        }
    }

    public void launchSettings(Context uiContext, DreamInfo dreamInfo) {
        logd("launchSettings(%s)", dreamInfo);
        if (dreamInfo == null || dreamInfo.settingsComponentName == null) {
            return;
        }
        uiContext.startActivity(new Intent().setComponent(dreamInfo.settingsComponentName));
    }

    public void preview(DreamInfo dreamInfo) {
        logd("preview(%s)", dreamInfo);
        if (mDreamManager == null || dreamInfo == null || dreamInfo.componentName == null)
            return;
        try {
            mDreamManager.testDream(mContext.getUserId(), dreamInfo.componentName);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to preview " + dreamInfo, e);
        }
    }

    public void startDreaming() {
        logd("startDreaming()");
        if (mDreamManager == null)
            return;
        try {
            mDreamManager.dream();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to dream", e);
        }
    }

    private static ComponentName getDreamComponentName(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
            return null;
        }
        return new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
    }

    private static final class DreamMetadata {
        @Nullable
        Drawable mPreviewImage;
        @Nullable
        ComponentName mSettingsActivity;
    }

    @Nullable
    private static TypedArray readMetadata(PackageManager pm, ServiceInfo serviceInfo) {
        if (serviceInfo == null || serviceInfo.metaData == null) {
            return null;
        }
        try (XmlResourceParser parser =
                     serviceInfo.loadXmlMetaData(pm, DreamService.DREAM_META_DATA)) {
            if (parser == null) {
                Log.w(TAG, "No " + DreamService.DREAM_META_DATA + " meta-data");
                return null;
            }
            Resources res = pm.getResourcesForApplication(serviceInfo.applicationInfo);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            while (true) {
                final int type = parser.next();
                if (type == XmlPullParser.END_DOCUMENT || type == XmlPullParser.START_TAG) {
                    break;
                }
            }
            String nodeName = parser.getName();
            if (!"dream".equals(nodeName)) {
                Log.w(TAG, "Meta-data does not start with dream tag");
                return null;
            }
            return res.obtainAttributes(attrs, com.android.internal.R.styleable.Dream);
        } catch (PackageManager.NameNotFoundException | IOException | XmlPullParserException e) {
            Log.w(TAG, "Error parsing : " + serviceInfo.packageName, e);
            return null;
        }
    }

    private static ComponentName convertToComponentName(String flattenedString,
            ServiceInfo serviceInfo) {
        if (flattenedString == null) return null;

        if (flattenedString.indexOf('/') < 0) {
            flattenedString = serviceInfo.packageName + "/" + flattenedString;
        }
        return ComponentName.unflattenFromString(flattenedString);
    }

    private static DreamMetadata getDreamMetadata(PackageManager pm, ResolveInfo resolveInfo) {
        DreamMetadata result = new DreamMetadata();
        if (resolveInfo == null) return result;
        TypedArray rawMetadata = readMetadata(pm, resolveInfo.serviceInfo);
        if (rawMetadata == null) return result;
        result.mSettingsActivity = convertToComponentName(rawMetadata.getString(
                com.android.internal.R.styleable.Dream_settingsActivity), resolveInfo.serviceInfo);
        result.mPreviewImage = rawMetadata.getDrawable(
                com.android.internal.R.styleable.Dream_previewImage);
        rawMetadata.recycle();
        return result;
    }

    private static void logd(String msg, Object... args) {
        if (DEBUG) {
            Log.d(TAG, args == null || args.length == 0 ? msg : String.format(msg, args));
        }
    }

    private static class DreamInfoComparator implements Comparator<DreamInfo> {
        private final ComponentName mDefaultDream;

        public DreamInfoComparator(ComponentName defaultDream) {
            mDefaultDream = defaultDream;
        }

        @Override
        public int compare(DreamInfo lhs, DreamInfo rhs) {
            return sortKey(lhs).compareTo(sortKey(rhs));
        }

        private String sortKey(DreamInfo di) {
            StringBuilder sb = new StringBuilder();
            sb.append(di.componentName.equals(mDefaultDream) ? '0' : '1');
            sb.append(di.caption);
            return sb.toString();
        }
    }
}
