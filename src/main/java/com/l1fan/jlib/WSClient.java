package com.l1fan.jlib;

import okhttp3.*;
import okio.ByteString;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * okhttp client websocket wrapper
 */
public class WSClient extends WebSocketListener {

    /**
     * schedule for interval send message and reconnect
     */
    private static ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();

    /**
     * websocket url
     */
    private String url;
    /**
     * send message
     */
    private List<String> sendText = new ArrayList<>();

    /**
     * loop send message and interval and future
     */
    private List<String> loopSendText = new ArrayList<>();
    private int interval;
    private ScheduledFuture<?> cancelInterval;

    /**
     * text/binary message consumer
     */
    private BiConsumer<WebSocket, String> textMessageConsumer;
    private BiConsumer<WebSocket, ByteString> binaryMessageConsumer;

    /**
     * okhttp client
     */
    private static OkHttpClient defaultOkhttp;
    private OkHttpClient okhttp;

    /**
     * reconnect min-max time (second)
     */
    private int delayMin = 1;
    private int delayMax = 10;
    private boolean reconnect = true;
    private ScheduledFuture<?> cancelReconnect;

    /**
     * event map
     */
    private Map<String, BiConsumer<WebSocket, String>> onMap = new HashMap<>();

    /**
     * raw okhttp websocket instance
     */
    private WebSocket rawWebSocket;
    /**
     * listener for extra jobs
     */
    private WebSocketListener listener;

    public WSClient() {
    }

    public static WSClient create() {
        return new WSClient();
    }

    /**
     * create a client with url
     *
     * @param url websocket url ws:// ro wss://
     */
    public static WSClient url(String url) {
        WSClient wsclient = new WSClient();
        wsclient.url = url;
        return wsclient;
    }

    /**
     * change ws url
     *
     * @param url websocket url
     */
    public void setUrl(String url) {
        this.url = url;
    }


    /**
     * send message before {@link WSClient#start()}
     *
     * @param sendText eg: subscribe text
     */
    public WSClient autosend(String sendText) {
        this.sendText.add(sendText);
        return this;
    }

    public WSClient autosend(List<String> sendText) {
        this.sendText.addAll(sendText);
        return this;
    }

    public WSClient autosend(String sendText, int interval) {
        this.loopSendText.add(sendText);
        this.interval = interval;
        return this;
    }

    public WSClient autosend(List<String> sendText, int interval) {
        this.loopSendText.addAll(sendText);
        this.interval = interval;
        return this;
    }

    public WSClient text(BiConsumer<WebSocket, String> consumer) {
        this.textMessageConsumer = consumer;
        return this;
    }

    public WSClient bytes(BiConsumer<WebSocket, ByteString> consumer) {
        this.binaryMessageConsumer = consumer;
        return this;
    }


    /**
     * custom okhttp client
     */
    public WSClient okhttp(OkHttpClient okhttp) {
        this.okhttp = okhttp;
        return this;
    }

    /**
     * reconnect delay seconds
     */
    public WSClient reconnect(int delayMin, int delayMax) {
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        return this;
    }

    /**
     * disable reconnect
     */
    public WSClient reconnect(boolean enable) {
        this.reconnect = enable;
        return this;
    }

    public WSClient listener(WebSocketListener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        if (listener != null) listener.onOpen(webSocket, response);
        emitEvent("open", webSocket, response.toString());

        sendText.forEach(webSocket::send);

        if (loopSendText.size() > 0) {
            cancelInterval = schedule.scheduleAtFixedRate(() -> loopSendText.forEach(webSocket::send), interval, interval, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (listener != null) listener.onMessage(webSocket, text);

        if (textMessageConsumer != null) {
            textMessageConsumer.accept(webSocket, text);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        if (listener != null) listener.onMessage(webSocket, bytes);

        if (binaryMessageConsumer != null) {
            binaryMessageConsumer.accept(webSocket, bytes);
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        if (listener != null) listener.onClosed(webSocket, code, reason);
        emitEvent("closed", webSocket, String.format("closed:[%s]%s", code, reason));

        _reconnect(webSocket, random());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        if (listener != null) listener.onClosing(webSocket, code, reason);
        emitEvent("closing", webSocket, String.format("closing:[%s]%s", code, reason));
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        if (listener != null) listener.onFailure(webSocket, t, response);
        emitEvent("error", webSocket, String.format("error:%s", t.getMessage()));

        _reconnect(webSocket, random());
    }

    private void emitEvent(String event, WebSocket webSocket, String info) {
        Optional.ofNullable(onMap.get(event))
                .ifPresent(b -> b.accept(webSocket, info));
    }

    private void _reconnect(WebSocket webSocket, int delay) {
        // cancel old loop send
        if (cancelInterval != null ) cancelInterval.cancel(true);
        if (cancelReconnect != null ) cancelReconnect.cancel(true);

        if (!reconnect) return;
        emitEvent("reconnect", rawWebSocket, String.format("reconnect: delay %s seconds", delay));
        // start new websocket
        cancelReconnect = schedule.schedule(() -> {
            rawWebSocket = okhttp.newWebSocket(webSocket.request(), this);
        }, random(), TimeUnit.SECONDS);
    }

    private int random() {
        return ThreadLocalRandom.current().nextInt(delayMin, delayMax + 1);
    }

    /**
     * send message , if you want message be saved and sent next time websocket connected , use autosend
     *
     * @param text
     * @return
     */
    public boolean send(String text) {
        if (rawWebSocket != null) {
            return rawWebSocket.send(text);
        }
        return false;
    }

    /**
     * real websocket start connect
     */
    public WSClient start() {
        Request req = new Request.Builder().url(url).build();
        if (okhttp == null) {
            okhttp = _okhttp();
        }

        rawWebSocket = okhttp.newWebSocket(req, this);
        return this;
    }

    public void pause() {
        reconnect = false;
        if (rawWebSocket != null) rawWebSocket.close(1000, "Normal Closure");
    }

    public void resume() {
        reconnect = true;
        _reconnect(rawWebSocket, 0);
    }

    /**
     * terminal all
     */
    public void stop() {
        pause();
        okhttp = null;
        schedule.shutdownNow();
    }

    /**
     * event listener
     *
     * @param event "open", "closing", "closed", "error"
     */
    public WSClient on(String event, BiConsumer<WebSocket, String> eventConsumer) {
        this.onMap.put(event, eventConsumer);
        return this;
    }

    /**
     * intercept with okhttp websocketlistener, default is null
     */
    public WSClient intercept(WebSocketListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * use default okhttp client , singleton instance
     */
    private static OkHttpClient _okhttp() {
        if (defaultOkhttp == null) {
            defaultOkhttp = new OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build();
        }
        return defaultOkhttp;
    }
}
