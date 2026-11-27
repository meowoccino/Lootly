package com.lootly.app

import android.app.Application
import com.lootly.app.data.auth.TwitchAccountRepository
import com.lootly.app.data.auth.TwitchSessionStore
import com.lootly.app.data.state.MinerRepository

class TwitchMinerApp : Application() {
    lateinit var sessionStore: TwitchSessionStore
        private set
    lateinit var accountRepository: TwitchAccountRepository
        private set
    lateinit var minerRepository: MinerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = TwitchSessionStore(applicationContext)
        accountRepository = TwitchAccountRepository()
        minerRepository = MinerRepository()
    }
}
