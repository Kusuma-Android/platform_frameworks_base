/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.keyguard;

import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_DEVICE_ADMIN;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_NONE;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_PREPARE_FOR_UPDATE;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_RESTART;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_TIMEOUT;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_USER_REQUEST;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import com.android.internal.widget.LockscreenCredential;
import com.android.systemui.R;

/**
 * A Pin based Keyguard input view
 */
public abstract class KeyguardPinBasedInputView extends KeyguardAbsKeyInputView {

    protected PasswordTextView mPasswordEntry;
    private NumPadButton mOkButton;
    private NumPadButton mDeleteButton;
    private NumPadKey[] mButtons = new NumPadKey[10];

    public KeyguardPinBasedInputView(Context context) {
        this(context, null);
    }

    public KeyguardPinBasedInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // send focus to the password field
        return mPasswordEntry.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    protected void resetState() {
    }

    @Override
    protected void setPasswordEntryEnabled(boolean enabled) {
        mPasswordEntry.setEnabled(enabled);
        mOkButton.setEnabled(enabled);
        if (enabled && !mPasswordEntry.hasFocus()) {
            mPasswordEntry.requestFocus();
        }
    }

    @Override
    protected void setPasswordEntryInputEnabled(boolean enabled) {
        mPasswordEntry.setEnabled(enabled);
        mOkButton.setEnabled(enabled);
        if (enabled && !mPasswordEntry.hasFocus()) {
            mPasswordEntry.requestFocus();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.isConfirmKey(keyCode)) {
            mOkButton.performClick();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            mDeleteButton.performClick();
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int number = keyCode - KeyEvent.KEYCODE_0;
            performNumberClick(number);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            int number = keyCode - KeyEvent.KEYCODE_NUMPAD_0;
            performNumberClick(number);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected int getPromptReasonStringRes(int reason) {
        switch (reason) {
            case PROMPT_REASON_RESTART:
                return R.string.kg_prompt_reason_restart_pin;
            case PROMPT_REASON_TIMEOUT:
                return R.string.kg_prompt_reason_timeout_pin;
            case PROMPT_REASON_DEVICE_ADMIN:
                return R.string.kg_prompt_reason_device_admin;
            case PROMPT_REASON_USER_REQUEST:
                return R.string.kg_prompt_reason_user_request;
            case PROMPT_REASON_PREPARE_FOR_UPDATE:
                return R.string.kg_prompt_reason_timeout_pin;
            case PROMPT_REASON_NONE:
                return 0;
            default:
                return R.string.kg_prompt_reason_timeout_pin;
        }
    }

    private void performNumberClick(int number) {
        if (number >= 0 && number <= 9) {
            mButtons[number].performClick();
        }
    }

    @Override
    protected void resetPasswordText(boolean animate, boolean announce) {
        mPasswordEntry.reset(animate, announce);
    }

    @Override
    protected LockscreenCredential getEnteredCredential() {
        return LockscreenCredential.createPinOrNone(mPasswordEntry.getText());
    }

    @Override
    protected void onFinishInflate() {
        mPasswordEntry = findViewById(getPasswordTextViewId());

        // Set selected property on so the view can send accessibility events.
        mPasswordEntry.setSelected(true);

        mOkButton = findViewById(R.id.key_enter);

        mDeleteButton = findViewById(R.id.delete_button);
        mDeleteButton.setVisibility(View.VISIBLE);

        mButtons[0] = findViewById(R.id.key0);
        mButtons[1] = findViewById(R.id.key1);
        mButtons[2] = findViewById(R.id.key2);
        mButtons[3] = findViewById(R.id.key3);
        mButtons[4] = findViewById(R.id.key4);
        mButtons[5] = findViewById(R.id.key5);
        mButtons[6] = findViewById(R.id.key6);
        mButtons[7] = findViewById(R.id.key7);
        mButtons[8] = findViewById(R.id.key8);
        mButtons[9] = findViewById(R.id.key9);

        mPasswordEntry.requestFocus();
        super.onFinishInflate();
        reloadColors();
    }

    /**
     * By default, the new layout will be enabled. When false, revert to the old style.
     */
    public void setIsNewLayoutEnabled(boolean isEnabled) {
        if (!isEnabled) {
            for (int i = 0; i < mButtons.length; i++) {
                mButtons[i].disableNewLayout();
            }
            mDeleteButton.disableNewLayout();
            mOkButton.disableNewLayout();
            reloadColors();
        }
    }

    /**
     * Reload colors from resources.
     **/
    public void reloadColors() {
        for (NumPadKey key : mButtons) {
            key.reloadColors();
        }
        mPasswordEntry.reloadColors();
        mDeleteButton.reloadColors();
        mOkButton.reloadColors();
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(
                com.android.internal.R.string.keyguard_accessibility_pin_unlock);
    }
}
