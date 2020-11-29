# KEvent  
[![Kotlin 1.4.10](https://img.shields.io/badge/Kotlin-1.4.20-blue.svg)](http://kotlinlang.org) [![JCenter Version](https://img.shields.io/bintray/v/rationalityfrontline/kevent/kevent?label=JCenter)](https://bintray.com/rationalityfrontline/kevent/kevent) [![Apache License 2.0](https://img.shields.io/github/license/rationalityfrontline/kevent)](https://github.com/RationalityFrontline/kevent/blob/master/LICENSE) ![language](https://img.shields.io/badge/100%25-kotlin-orange)

A powerful in-process event dispatcher based on Kotlin and Coroutines.

## Feature List
* Implement publish–subscribe pattern
* Tiny (56.4kb jar) and super fast (no reflection)
* Usable in plenty scenarios: plain kotlin, server side, android, javafx, swing
* Use Enum as event type, so you don't have to create numerous event classes
* Support 4 event dispatch modes with 3 subscriber thread modes

  | DispatchMode\\ThreadMode | POSTING | BACKGROUND | UI |
  |--------------------------|:-------:|:----------:|:----:|
  | INSTANTLY                | √       | ×          | ×  |
  | SEQUENTIAL               | ×       | √          | √  |
  | CONCURRENT               | ×       | √          | ×  |
  | ORDERED\_CONCURRENT      | ×       | √          | ×  |
* Support a bunch of advanced features:
  * event blocking
  * event dispatch cancellation
  * sticky events
  * subscriber priority
  * subscriber tag
  * subscribe multiple event types with same subscriber
  * multiple ways to subscribe and unsubscribe
  * provide a helpful subscriber interface
* Thread safe
* Fully tested
 
## Download
**Gradle Kotlin DSL**
```groovy
implementation("org.rationalityfrontline:kevent:1.0.0")
```
**Gradle Groovy DSL**
```groovy
implementation 'org.rationalityfrontline:kevent:1.0.0'
```
**Maven**
```xml
<dependency>
    <groupId>org.rationalityfrontline</groupId>
    <artifactId>kevent</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
</dependency>
```

## Documentation
**Basic usage:**
```kotlin
import java.lang.ClassCastException

enum class EventTypes {
    UNIT_EVENT,
    STRING_EVENT,
}

private class ExampleSubscriber : KEventSubscriber {
    fun registerSubscribers() {
        subscribe<Unit>(EventTypes.UNIT_EVENT) { event ->
            println("${"ExampleSubscriber.lambda".padEnd(35)}: $event")
        }
        subscribe(EventTypes.STRING_EVENT, ::onStringEvent)
        subscribeMultiple(listOf(
            EventTypes.UNIT_EVENT,
            EventTypes.STRING_EVENT,
        ), ::onAnyEvent)
    }

    fun unregisterSubscribers() {
        unsubscribeAll()
    }

    private fun onStringEvent(event: Event<String>) {
        println("${"ExampleSubscriber.onStringEvent".padEnd(35)}: $event")
    }

    private fun onAnyEvent(event: Event<Any>) {
        try {
            when (event.type) {
                EventTypes.UNIT_EVENT -> event.data as Unit
                EventTypes.STRING_EVENT -> event.data as String
            }
        } catch (e: ClassCastException) {
            println("Different event data types might come with same event type: ${e.message}")
        }
        println("${"ExampleSubscriber.onAnyEvent".padEnd(35)}: $event")
    }
}

private fun topLevelOnStringEvent(event: Event<String>) {
    println("${"topLevelOnStringEvent".padEnd(35)}: $event")
}

fun main() {
    KEvent.subscribe<Unit>(EventTypes.UNIT_EVENT, tag = "main") { event ->
        println("${"main.lambda".padEnd(35)}: $event")
    }
    KEvent.subscribe(EventTypes.STRING_EVENT, ::topLevelOnStringEvent)

    val subscriber = ExampleSubscriber()
    subscriber.registerSubscribers()

    KEvent.post(EventTypes.UNIT_EVENT, Unit)
    KEvent.post(EventTypes.STRING_EVENT, "KEvent is awesome!")
    KEvent.post(EventTypes.STRING_EVENT, 42)

    subscriber.unregisterSubscribers()

    KEvent.removeSubscribersByTag("main")
    KEvent.unsubscribe(EventTypes.STRING_EVENT, ::topLevelOnStringEvent)
}
```
Running the code above will produce the following outputs:
```text
main.lambda                        : Event(type=UNIT_EVENT, data=kotlin.Unit, dispatchMode=INSTANTLY, isSticky=false)
ExampleSubscriber.lambda           : Event(type=UNIT_EVENT, data=kotlin.Unit, dispatchMode=INSTANTLY, isSticky=false)
ExampleSubscriber.onAnyEvent       : Event(type=UNIT_EVENT, data=kotlin.Unit, dispatchMode=INSTANTLY, isSticky=false)
topLevelOnStringEvent              : Event(type=STRING_EVENT, data=KEvent is awesome!, dispatchMode=INSTANTLY, isSticky=false)
ExampleSubscriber.onStringEvent    : Event(type=STRING_EVENT, data=KEvent is awesome!, dispatchMode=INSTANTLY, isSticky=false)
ExampleSubscriber.onAnyEvent       : Event(type=STRING_EVENT, data=KEvent is awesome!, dispatchMode=INSTANTLY, isSticky=false)
topLevelOnStringEvent              : Event(type=STRING_EVENT, data=42, dispatchMode=INSTANTLY, isSticky=false)
ExampleSubscriber.onStringEvent    : Event(type=STRING_EVENT, data=42, dispatchMode=INSTANTLY, isSticky=false)
Different event data types might come with same event type: class java.lang.Integer cannot be cast to class java.lang.String (java.lang.Integer and java.lang.String are in module java.base of loader 'bootstrap')
ExampleSubscriber.onAnyEvent       : Event(type=STRING_EVENT, data=42, dispatchMode=INSTANTLY, isSticky=false)
```
For advanced features, please refer to the corresponding test specifications:
* [event blocking](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/EventBlockingFeature.kt)
* [event dispatch cancellation](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/EventCancellingFeature.kt)
* [sticky events](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/StickyEventFeature.kt)
* [subscriber priority](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/SubscriberPriorityFeature.kt)
* [subscriber tag](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/SubscriberTagFeature.kt)

## Performance
There is a [sample benchmark code](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/PerformanceBenchmark.kt) in the repository, 
you can clone this repository and run the benchmark on your own machine.

## License

KEvent is released under the [Apache 2.0 license](https://github.com/RationalityFrontline/kevent/blob/master/LICENSE).

```text
Copyright 2020 RationalityFrontline

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```