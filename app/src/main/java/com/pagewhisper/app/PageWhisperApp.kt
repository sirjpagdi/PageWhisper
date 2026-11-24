package com.pagewhisper.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.playback.Playback

class PageWhisperApp : Application() {
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Playback.init(this)
        // Tells the playback engine whether a screen is on display (it keeps a bigger audio reserve when not).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++; Playback.appVisible = true }
            override fun onActivityStopped(activity: Activity) { started = maxOf(0, started - 1); Playback.appVisible = started > 0 }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
