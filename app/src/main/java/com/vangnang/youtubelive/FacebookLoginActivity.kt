package com.vangnang.youtubelive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult

/** Owns the portrait authorization flow; returning reveals the landscape camera activity. */
class FacebookLoginActivity : Activity() {
    private lateinit var callbacks: CallbackManager
    private var launchPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Đang mở đăng nhập Facebook…"
            gravity = Gravity.CENTER
            textSize = 18f
        })
        if (FacebookPermissionState.loginPermissions(intent.getStringExtra("login_target") ?: "").isEmpty()) {
            setResult(RESULT_CANCELED); finish(); return
        }
        callbacks = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(callbacks, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                setResult(RESULT_OK, Intent().putExtra("login_target", intent.getStringExtra("login_target") ?: "choose")); finish()
            }
            override fun onCancel() { setResult(RESULT_CANCELED); finish() }
            override fun onError(error: FacebookException) {
                setResult(RESULT_CANCELED, Intent().putExtra("login_error", true)); finish()
            }
        })
        launchPending = savedInstanceState?.getBoolean("launch_pending") ?: true
    }

    // Wait until the portrait window is laid out before the SDK sizes its login dialog.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && launchPending && ::callbacks.isInitialized) {
            launchPending = false
            LoginManager.getInstance().logIn(this, FacebookPermissionState.loginPermissions(intent.getStringExtra("login_target") ?: "choose"))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launch_pending", launchPending)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbacks.onActivityResult(requestCode, resultCode, data)
    }

    companion object { const val REQUEST_CODE = 7312 }
}
