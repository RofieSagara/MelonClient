package com.sagara.melon

import android.util.Log
import com.google.gson.GsonBuilder
import io.github.centrifugal.centrifuge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.shareIn
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

data class Message(val id: String, val target: String, val message: String)

class MelonClient(private val host: String): CoroutineScope {
    private val gson = GsonBuilder().create()
    private var _onMessage = MutableSharedFlow<Message>()
    val message = _onMessage.asSharedFlow()
    private var _client: Client? = null
    private var _isConnected = false
    private val tag = "MelonClient"
    private var _username = ""

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    fun connect(username: String) = connect(username, null)
    fun connect(username: String, onReady: (() -> Unit)? = null) {
        _username = username
        if (_client == null || !_isConnected) {
            _client = Client(host, Options(), object : EventListener() {
                override fun onConnect(client: Client?, event: ConnectEvent?) {
                    _isConnected = true
                    client?.newSubscription(
                        "${username}_message",
                        object : SubscriptionEventListener() {
                            override fun onPublish(sub: Subscription?, event: PublishEvent?) {
                                Log.d(
                                    tag,
                                    "publish coming to ${sub?.channel} with data ${
                                        event?.data?.toString(charset = Charset.defaultCharset())
                                    }"
                                )
                                runCatching {
                                    if (event != null) {
                                        val data = gson.fromJson(
                                            event.data.toString(charset = Charset.defaultCharset()),
                                            Message::class.java
                                        )
                                        Log.d(tag, data.toString())
                                        launch {
                                            _onMessage.emit(data)
                                        }
                                    }
                                }
                            }

                            override fun onSubscribeSuccess(
                                sub: Subscription?,
                                event: SubscribeSuccessEvent?
                            ) {
                                Log.i(tag, "client SubscribeSuccess ${sub?.channel}")
                                onReady?.invoke()
                            }

                            override fun onSubscribeError(
                                sub: Subscription?,
                                event: SubscribeErrorEvent?
                            ) {
                                Log.e(
                                    tag,
                                    "client SubscribeError ${sub?.channel} cause ${event?.message}"
                                )
                            }
                        })
                }

                override fun onDisconnect(client: Client?, event: DisconnectEvent?) {
                    _isConnected = false
                    Log.w(tag, "client Disconnect cause ${event?.reason}")
                }

                override fun onError(client: Client?, event: ErrorEvent?) {
                    Log.e(tag, "client Error cause ${event?.exception}")
                }
            })
            _client?.setConnectData(username.toByteArray())
            _client?.connect()
        } else {
            _client?.setConnectData(username.toByteArray())
            _client?.connect()
        }
    }

    fun report(id: String) {
        if (_client != null && _isConnected) {
            _client?.rpc("report_message", id.toByteArray(), object : ReplyCallback<RPCResult> {
                override fun onFailure(e: Throwable?) {
                    Log.e(tag, "client failed send rpc to server", e)
                }

                override fun onDone(error: ReplyError?, result: RPCResult?) {
                    Log.i(tag, "rpc send success")
                }
            })
        } else {
            connect(_username) { report(id) }
        }
    }

    fun requestMessage() {
        if (_client != null && _isConnected) {
            _client?.rpc("request_message", _username.toByteArray(), object : ReplyCallback<RPCResult> {
                override fun onFailure(e: Throwable?) {
                    Log.e(tag, "client failed send rpc to server", e)
                }

                override fun onDone(error: ReplyError?, result: RPCResult?) {
                    Log.i(tag, "rpc send success")
                }
            })
        } else {
            connect(_username) { requestMessage() }
        }
    }
}