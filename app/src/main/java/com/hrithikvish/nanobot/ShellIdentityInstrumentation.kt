package com.hrithikvish.nanobot

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CountDownLatch

/**
 * Starts NanoBot with the shell identity, so it holds `EXECUTE_APP_FUNCTIONS`.
 * `run_privileged.sh` starts it with `am instrument`. Adapted from github.com/android/appfunctions (Apache 2.0).
 */
class ShellIdentityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent.makeMainActivity(ComponentName(targetContext, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val automation = uiAutomation
        val error = if (automation == null) {
            "UiAutomation is null. Another instrumentation may be running."
        } else {
            try {
                automation.adoptShellPermissionIdentity("android.permission.EXECUTE_APP_FUNCTIONS")
                null
            } catch (e: SecurityException) {
                // The shell delegates to one instrumentation at a time, for example the AppFunctions sample agent.
                "${e.message}. Stop the other instrumentation, then run run_privileged.sh again."
            }
        }
        error?.let { intent.putExtra(MainActivity.EXTRA_INSTRUMENTATION_ERROR, it) }
        targetContext.startActivity(intent)

        // The shell identity stays only while this instrumentation runs.
        try {
            CountDownLatch(1).await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
