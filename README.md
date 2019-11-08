# wsclient
simple java websocket auto reconnect client. also can pause/resume it. use [OkHttp](https://github.com/square/okhttp):sparkles: 

## Usage
java
```java
WSClient.url("wss://echo.websocket.org")
        .autosend("send message whenever websocket (re)connected")
        .text((ws, message) -> System.out.println("receive text: " + message)) 
        .start();
```

kotlin
```kotlin
WSClient.url("wss://echo.websocket.org")
        .autosend("send message whenever websocket (re)connected")
        .text{ws, message -> println("receive text: $message")}
        .start();
```

## More Usage
```java
WSClient client = WSClient
    // ws or wss
    .url("ws://") 
    
    // eg: subscribe info
    .autosend("auto send message once")     
    // x seconds, eg: custom ping string.
    .autosend("auto send interval", 5)   
    // receive text message
    .text((ws, string) -> ...)    
    // receive binary message
    .bytes((ws, bytestring) -> ...)   
    
    // options
    // use your own okhttp instance, default use a internal singleton instance
    .okhttp(instance) 
    // reconnect delay random seconds , default  1 - 10 seconds
    .reconnect(minDelay, maxDelay)
    // event listener, reconnet/open/closing/closed/error
    .on("error", (ws, info) ->  )  
    // intercept with raw okhttp websocketlistener
    .intercept(new websocketListener(){...}) 
    
    //start websocket 
    .start()             

// return false if websocket is not opened
boolean success = client.send("normal send message")  

// lifecycle
client.pause()    // close websocket and disable reconnect
client.resume()   // reconnect ws
client.stop()     // terminate all
```

## Releases
Maven
```xml
<dependency>
    <groupId>com.l1fan.jlib</groupId>
    <artifactId>wsclient</artifactId>
    <version>1.0</version>
</dependency>
```
Gradle
```groovy
implementation "com.l1fan.jlib:wsclient:1.0"
```