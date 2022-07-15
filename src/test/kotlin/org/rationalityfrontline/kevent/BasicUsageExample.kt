package org.rationalityfrontline.kevent

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