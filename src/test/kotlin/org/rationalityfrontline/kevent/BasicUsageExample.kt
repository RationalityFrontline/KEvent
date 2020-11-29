package org.rationalityfrontline.kevent

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