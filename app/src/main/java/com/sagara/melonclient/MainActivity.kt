package com.sagara.melonclient

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.coroutineScope
import com.sagara.melon.MelonClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val client = MelonClient("ws://10.0.2.2:8080/connection/websocket?format=protobuf")
        lifecycle.coroutineScope.launch {
            client.message.onEach { Log.d("MainActivity", it.toString()) }
                .onEach { client.report(it.id) }
                .collect()
        }
        client.connect("android_emulator") { client.requestMessage() }
    }
}