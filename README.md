# KEvent
[![Maven Central](https://img.shields.io/maven-central/v/org.rationalityfrontline/kevent.svg?label=maven%20central)](https://search.maven.org/search?q=g:%22org.rationalityfrontline%22%20AND%20a:%22kevent%22)
[![Kotlin 1.9.22](https://img.shields.io/badge/kotlin-1.9.22-blue.svg)](http://kotlinlang.org)
![JDK](https://img.shields.io/badge/jdk-%3E%3D11-orange)
[![Apache License 2.0](https://img.shields.io/github/license/rationalityfrontline/kevent)](https://github.com/RationalityFrontline/kevent/blob/master/LICENSE)
[![Awesome Kotlin Badge](https://kotlin.link/awesome-kotlin.svg)](https://github.com/KotlinBy/awesome-kotlin)

A powerful in-process event dispatcher based on Kotlin and Coroutines.

## Feature List
* Implement publish–subscribe pattern
* Tiny (52.2kb jar) and super-fast (no reflection)
* Usable in plenty scenarios: plain kotlin, server side, android, javafx, swing
* Use Enum as event type, so you don't have to create numerous event classes
* Support 3 event dispatch modes with 3 subscriber thread modes

  | DispatchMode\\ThreadMode | POSTING | BACKGROUND | UI |
  |--------------------------|:-------:|:----------:|:----:|
  | POSTING                  | √       | ×          | ×  |
  | SEQUENTIAL               | ×       | √          | √  |
  | CONCURRENT               | ×       | √          | ×  |
* Support a bunch of advanced features:
  * event blocking
  * event dispatch cancellation
  * sticky events
  * subscriber management
  * subscriber priority
  * subscriber tag
  * subscribe multiple event types with same subscriber
  * multiple ways to subscribe and unsubscribe
  * provide a helpful subscriber interface
* Thread safe
* Fully tested
 
## Download
**Gradle Kotlin DSL**
```kotlin
implementation("org.rationalityfrontline:kevent:2.3.1")
```

**Maven**
```xml
<dependency>
    <groupId>org.rationalityfrontline</groupId>
    <artifactId>kevent</artifactId>
    <version>2.3.1</version>
</dependency>
```

## Usage
```kotlin
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
    KEVENT.subscribe<Unit>(EventTypes.UNIT_EVENT, tag = "main") { event ->
        println("${"main.lambda".padEnd(35)}: $event")
    }
    KEVENT.subscribe(EventTypes.STRING_EVENT, ::topLevelOnStringEvent)

    val subscriber = ExampleSubscriber()
    subscriber.registerSubscribers()

    KEVENT.post(EventTypes.UNIT_EVENT, Unit)
    KEVENT.post(EventTypes.STRING_EVENT, "KEvent is awesome!")
    KEVENT.post(EventTypes.STRING_EVENT, 42)

    subscriber.unregisterSubscribers()

    KEVENT.removeSubscribersByTag("main")
    KEVENT.unsubscribe(EventTypes.STRING_EVENT, ::topLevelOnStringEvent)
}
```
Running the code above will produce the following outputs:
```text
main.lambda                        : Event(type=UNIT_EVENT, data=kotlin.Unit, dispatchMode=POSTING, isSticky=false)
ExampleSubscriber.lambda           : Event(type=UNIT_EVENT, data=kotlin.Unit, dispatchMode=POSTING, isSticky=false)
ExampleSubscriber.onAnyEvent       : Event(type=UNIT_EVENT, data=kotlin.Unit, dispatchMode=POSTING, isSticky=false)
topLevelOnStringEvent              : Event(type=STRING_EVENT, data=KEvent is awesome!, dispatchMode=POSTING, isSticky=false)
ExampleSubscriber.onStringEvent    : Event(type=STRING_EVENT, data=KEvent is awesome!, dispatchMode=POSTING, isSticky=false)
ExampleSubscriber.onAnyEvent       : Event(type=STRING_EVENT, data=KEvent is awesome!, dispatchMode=POSTING, isSticky=false)
topLevelOnStringEvent              : Event(type=STRING_EVENT, data=42, dispatchMode=POSTING, isSticky=false)
ExampleSubscriber.onStringEvent    : Event(type=STRING_EVENT, data=42, dispatchMode=POSTING, isSticky=false)
Different event data types might come with same event type: class java.lang.Integer cannot be cast to class java.lang.String (java.lang.Integer and java.lang.String are in module java.base of loader 'bootstrap')
ExampleSubscriber.onAnyEvent       : Event(type=STRING_EVENT, data=42, dispatchMode=POSTING, isSticky=false)
```
For advanced features, please refer to the corresponding test specifications:
* [threading (dispatch modes and thread modes)](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/ThreadingFeature.kt)
* [event blocking](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/EventBlockingFeature.kt)
* [event dispatch cancellation](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/EventCancellingFeature.kt)
* [sticky events](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/StickyEventFeature.kt)
* [subscriber priority](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/SubscriberPriorityFeature.kt)
* [subscriber tag](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/SubscriberTagFeature.kt)
## Performance
There is a [sample benchmark code](https://github.com/RationalityFrontline/kevent/blob/master/src/test/kotlin/org/rationalityfrontline/kevent/PerformanceBenchmark.kt) in the repository, 
you can clone this repository and run the benchmark on your own machine. Here is the benchmark results on my machine:

| Conditions\\AvgCallTime(ms)\\ThreadMode    | POSTING      | SEQUENTIAL  | CONCURRENT   |
|--------------------------------------------|--------------|-------------|--------------|
| event-1; subs-10000; tc-false; st-false    | 4.6920002E-5 | 0.00142243  | 8.7184E-4    |
| event-1; subs-10000; tc-true; st-false     | 10.446612    | 10.578969   | 1.3352561    |
| event-10000; subs-1; tc-false; st-false    | 0.00128533   | 0.00481413  | 0.0026817601 |
| event-10000; subs-1; tc-true; st-false     | 10.942825    | 1.3770571   | 1.3426003    |
| event-1000; subs-10000; tc-false; st-false | 2.238846E-5  | 3.071547E-4 | 6.2108616E-4 |
| event-1; subs-10000; tc-false; st-true     |              |             | 6.9659E-4    |
| event-1; subs-10000; tc-true; st-true      |              |             | 1.3062543    |
| event-10000; subs-1; tc-false; st-true     |              |             | 0.0034271    |
| event-10000; subs-1; tc-true; st-true      |              |             | 1.3126742    |
| event-1000; subs-10000; tc-false; st-true  |              |             | 6.246506E-4  |

*event = event num<br>
subs = subscriber num<br>
tc = if subscribers are time-consuming (sleep 10ms in the above benchmark)<br>
st = if the event is sticky (and subscribers are added after the event was posted)*

```text
[Machine Info]
CPU: Intel(R) Core(TM) i7-4710MQ @ 2.50GHz (4C8T)
Memory: 8 + 8 = 16GB, DDR3, 1600MHz
OS: Window 10 Enterprise 64 bit version 1909
JDK: OpenJDK 17+35-2724 64 bit
```

## License

KEvent is released under the [Apache 2.0 license](https://github.com/RationalityFrontline/kevent/blob/master/LICENSE).

```text
Copyright 2020-2024 RationalityFrontline

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