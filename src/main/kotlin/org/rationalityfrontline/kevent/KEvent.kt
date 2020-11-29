/*
 * Copyright 2020 RationalityFrontline
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package org.rationalityfrontline.kevent

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Event
 *
 * @constructor Create a new event.
 * @param type event type, must be [Enum].
 * @param data event data, can be [Any], use [Unit] if no data is needed.
 * @param dispatchMode event dispatch mode, see [KEvent.DispatchMode], default to [KEvent.DispatchMode.INSTANTLY]
 * @param isSticky whether this event is sticky. Sticky event will be stored in the [KEvent]
 * until it get removed manually by calling [KEvent.removeStickyEvent], [KEvent.removeAllStickyEvents] or [KEvent.clear].
 * The event get dispatched normally for already existing subscribers (note that the event object received by
 * existing subscribers will get its [isSticky] property modified to false). For subscribers added after posting,
 * they will receive this event on subscription (with the original true [isSticky] property).
 * @property isPostedSticky whether this event is posted as sticky event.
 */
data class Event<T : Any>(
    val type: Enum<*>,
    val data: T,
    val dispatchMode: KEvent.DispatchMode = KEvent.DispatchMode.INSTANTLY,
    val isSticky: Boolean = false,
) {
    val isPostedSticky: Boolean get() = KEvent.containsStickyEvent(this as Event<Any>)
}


/**
 * Consumer function of the event.
 */
typealias EventConsumer<T> = (Event<T>) -> Unit


/**
 * Event subscriber.
 *
 * @constructor Create a subscriber.
 * @param consumer event consumer function.
 * @param threadMode specifies on which thread the subscriber will be called, see [KEvent.ThreadMode].
 * @param priority subscriber priority, bigger is higher, default to 0.
 * @param tag optional tag that is useful for unsubscription, see [KEvent.removeSubscribersByTag].
 */
private data class Subscriber<T : Any>(
    val consumer: EventConsumer<T>,
    val threadMode: KEvent.ThreadMode = KEvent.ThreadMode.BACKGROUND,
    val priority: Int = 0,
    val tag: String = "",
)

/**
 * A lightweight but powerful event dispatcher.
 */
object KEvent {

    /**
     * Specifies on which thread the subscriber will be called.
     */
    enum class ThreadMode {
        /**
         * Subscriber will be called on the same thread that the event get posted,
         * only events posted with dispatch mode [DispatchMode.INSTANTLY] will be dispatched to
         * subscribers of this thread mode.
         */
        POSTING,

        /**
         * Subscriber will be called on background worker thread, see [Dispatchers.Default].
         *
         * Incompatible with [DispatchMode.INSTANTLY]
         */
        BACKGROUND,

        /**
         * Subscriber will be called on UI thread, see [Dispatchers.Main].
         *
         * Incompatible with [DispatchMode.INSTANTLY]
         */
        UI,
    }

    /**
     * Specifies how the event will be dispatched to subscribers.
     */
    enum class DispatchMode {
        /**
         * Subscriber will be called instantly and sequentially(one by one according to their priority)
         * on the same thread that the event get posted,
         * only subscribers with thread mode [ThreadMode.POSTING] will be called.
         */
        INSTANTLY,

        /**
         * Subscribers will receive the event sequentially (one by one according to their priority).
         */
        SEQUENTIAL,

        /**
         * Subscribers will receive the event concurrently, the receive order is non-deterministic.
         *
         * If subscribers are not time consuming, please use [INSTANTLY] or [SEQUENTIAL] for better performance.
         */
        CONCURRENT,

        /**
         * Subscribers will receive the event concurrently, the receive order is determined by their priority.
         * In order to ensure the receive order, there will be a 1 millisecond delay between each dispatching
         * action. If the receive order doesn't matter, please use [CONCURRENT].
         *
         * If subscribers are not time consuming, please use [INSTANTLY] or [SEQUENTIAL] for better performance.
         */
        ORDERED_CONCURRENT,
    }

    private val logger = KotlinLogging.logger { }

    private val scope = CoroutineScope(Dispatchers.Default + CoroutineName("KEvent") + SupervisorJob())

    private val subscribersMap = ConcurrentHashMap<Enum<*>, MutableList<Subscriber<Any>>>()
    private val subscribersReadOnlyMap = ConcurrentHashMap<Enum<*>, Array<Subscriber<Any>>>()
    private val eventChannel = Channel<Event<Any>>(Channel.Factory.UNLIMITED)
    private val stickyEvents = Collections.synchronizedList(mutableListOf<Event<Any>>())
    private val cancelledEvents = Collections.synchronizedSet(mutableSetOf<Event<Any>>())
    private val blockedEventTypeMap = ConcurrentHashMap<Enum<*>, Boolean>()

    val DEFAULT_THREAD_MODE = ThreadMode.POSTING
    const val DEFAULT_PRIORITY = 0
    const val DEFAULT_TAG = ""

    init {
        scope.launch {
            eventChannel.consumeAsFlow().collect { event ->
                val subscriberList = subscribersReadOnlyMap[event.type]
                if (subscriberList == null || subscriberList.isEmpty()) {
                    logger.warn { "No subscribers for event type \"${event.type.name}\"" }
                } else {
                    val e = if (event.isSticky) event.copy(isSticky = false) else event

                    fun removeCancelledEvent() {
                        cancelledEvents.remove(e)
                        if (e != event) cancelledEvents.remove(event)
                    }

                    when (e.dispatchMode) {
                        DispatchMode.CONCURRENT -> {
                            // TODO: 2020/11/27 Improve performance, see https://github.com/Kotlin/kotlinx.coroutines/issues/2414
                            subscriberList.forEach { subscriber ->
                                if (!dispatchEventAsync(e, subscriber)) return@forEach
                            }
                            removeCancelledEvent()
                        }
                        DispatchMode.SEQUENTIAL -> {
                            scope.launch {
                                subscriberList.forEach { subscriber ->
                                    if (!dispatchEventSync(e, subscriber)) return@forEach
                                }
                                removeCancelledEvent()
                            }
                        }
                        DispatchMode.ORDERED_CONCURRENT -> {
                            scope.launch {
                                subscriberList.forEach { subscriber ->
                                    if (!dispatchEventAsync(e, subscriber)) return@forEach
                                    delay(1)
                                }
                                removeCancelledEvent()
                            }
                        }
                        DispatchMode.INSTANTLY -> {
                            logger.error { "Failed to dispatch event \"$event\": unexpected dispatch mode in event channel (\"INSTANTLY\")" }
                        }
                    }
                }
            }
        }
    }

    private inline fun isValidEvent(event: Event<Any>): Boolean {
        return !cancelledEvents.contains(event)
    }

    private inline fun getDispatchContext(subscriber: Subscriber<Any>, event: Event<Any>): CoroutineContext? {
        return when (subscriber.threadMode) {
            ThreadMode.BACKGROUND -> scope.coroutineContext
            ThreadMode.UI -> when (event.dispatchMode) {
                DispatchMode.SEQUENTIAL -> Dispatchers.Main
                else -> {
                    logger.error { "Error happened when dispatching event \"$event\": subscriber thread mode \"UI\" is only compatible with dispatch mode \"SEQUENTIAL\"" }
                    null
                }
            }
            ThreadMode.POSTING -> {
                logger.error { "Error happened when dispatching event \"$event\": subscriber thread mode \"POSTING\" is only compatible with dispatch mode \"INSTANTLY\"" }
                null
            }
        }
    }

    private inline fun consumeEvent(subscriber: Subscriber<Any>, event: Event<Any>) {
        try {
            subscriber.consumer(event)
        } catch (e: Exception) {
            logger.error { "Exception happened when calling subscriber of event \"$event\"\n${e.stackTraceToString()}" }
        }
    }

    private suspend fun dispatchEventSync(event: Event<Any>, subscriber: Subscriber<Any>): Boolean {
        if (!isValidEvent(event)) return false
        getDispatchContext(subscriber, event)?.run {
            withContext(this) {
                consumeEvent(subscriber, event)
            }
        }
        return true
    }

    private fun dispatchEventAsync(event: Event<Any>, subscriber: Subscriber<Any>): Boolean {
        if (!isValidEvent(event)) return false
        getDispatchContext(subscriber, event)?.run {
            scope.launch(this) {
                consumeEvent(subscriber, event)
            }
        }
        return true
    }

    /**
     * Post an event.
     *
     * @return true if the event is valid and there exist any subscriber of this event type, else false.
     */
    fun <T : Any> post(event: Event<T>): Boolean {
        if (blockedEventTypeMap.getOrDefault(event.type, false)) return false
        event as Event<Any>
        if (event.dispatchMode == DispatchMode.INSTANTLY) {
            if (event.isSticky) {
                logger.error { "Event with dispatch mode ${DispatchMode.INSTANTLY} can't be sticky: $event" }
                return false
            }
            val subscriberList = subscribersReadOnlyMap[event.type]?.run {
                filter { it.threadMode == ThreadMode.POSTING }
            }
            if (subscriberList == null || subscriberList.isEmpty()) {
                logger.warn { "No subscribers for event type \"${event.type.name}\" with dispatch mode ${DispatchMode.INSTANTLY}" }
                return false
            } else {
                subscriberList.forEach { subscriber ->
                    if (!isValidEvent(event)) return@forEach
                    consumeEvent(subscriber, event)
                }
                cancelledEvents.remove(event)
            }
        } else {
            if (event.isSticky) stickyEvents.add(event)
            scope.launch { eventChannel.send(event) }
        }
        return true
    }

    /**
     * Post an event.
     *
     * @param type event type, must be [Enum].
     * @param data event data, can be [Any], use [Unit] if no data is needed.
     * @param dispatchMode event dispatch mode, see [KEvent.DispatchMode], default to [KEvent.DispatchMode.INSTANTLY]
     * @param isSticky whether this event is sticky. Sticky event will be stored in the [KEvent]
     * until it get removed manually by calling [KEvent.removeStickyEvent], [KEvent.removeAllStickyEvents] or [KEvent.clear].
     * The event get dispatched normally for already existing subscribers (note that the event object received by
     * existing subscribers will get its [isSticky] property modified to false). For subscribers added after posting,
     * they will receive this event on subscription (with the original true [isSticky] property).
     * @return true if the event is valid and there exist any subscriber of this event type, else false.
     */
    inline fun <T : Any> post(
        type: Enum<*>,
        data: T,
        dispatchMode: DispatchMode = DispatchMode.INSTANTLY,
        isSticky: Boolean = false
    ): Boolean = post(Event(type, data, dispatchMode, isSticky))

    /**
     * Cancel further event dispatching, this should only be used together with [DispatchMode.SEQUENTIAL] or [DispatchMode.INSTANTLY].
     *
     * @return true if the event get cancelled, false if the event is already cancelled before.
     */
    fun <T : Any> cancelEvent(event: Event<T>): Boolean {
        event as Event<Any>
        return cancelledEvents.add(event)
    }

    /**
     * Remove the sticky [event].
     *
     * @return true if the event get removed, false if the event doesn't exit.
     */
    fun <T : Any> removeStickyEvent(event: Event<T>): Boolean {
        event as Event<Any>
        if (event.isSticky) {
            return stickyEvents.remove(event)
        } else {
            val e = event.copy(isSticky = true)
            synchronized(stickyEvents) {
                return stickyEvents.removeIf { it == e }
            }
        }
    }

    /**
     * Whether there is a sticky [event].
     */
    fun <T : Any> containsStickyEvent(event: Event<T>): Boolean {
        val e = if (event.isSticky) event else event.copy(isSticky = true)
        synchronized(stickyEvents) {
            return stickyEvents.find { it == e } != null
        }
    }

    /**
     * Clear all existing sticky events.
     */
    fun removeAllStickyEvents() {
        stickyEvents.clear()
    }

    private inline fun updateSubscribersReadOnlyMap(type: Enum<*>) {
        subscribersMap[type]?.run {
            subscribersReadOnlyMap[type] = toTypedArray()
        } ?: subscribersReadOnlyMap.remove(type)
    }

    private fun addSubscriber(eventType: Enum<*>, subscriber: Subscriber<Any>): Boolean {
        synchronized(subscribersMap) {
            subscribersMap.getOrPut(eventType) {
                Collections.synchronizedList(mutableListOf())
            }.apply {
                synchronized(this) {
                    if (find { it.consumer == subscriber.consumer } == null) {
                        add(subscriber)
                        sortByDescending { it.priority }
                        subscribersReadOnlyMap[eventType] = toTypedArray()
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Add a new subscriber. Subscribers with the same [consumer] can only be added once.
     *
     * @param eventType event type that this subscriber subscribers.
     * @param consumer event consumer function.
     * @param threadMode thread mode, see [ThreadMode].
     * @param priority priority, bigger is higher.
     * @param tag optional tag that is useful for unsubscription, see [KEvent.removeSubscribersByTag].
     * @return true if subscription is successful, else false.
     */
    fun <T : Any> subscribe(
        eventType: Enum<*>,
        threadMode: ThreadMode = DEFAULT_THREAD_MODE,
        priority: Int = DEFAULT_PRIORITY,
        tag: String = DEFAULT_TAG,
        consumer: EventConsumer<T>
    ): Boolean = subscribe(eventType, consumer, threadMode, priority, tag)

    /**
     * Add a new subscriber. Subscribers with the same [consumer] can only be added once.
     *
     * @param eventType event type that this subscriber subscribers.
     * @param consumer event consumer function.
     * @param threadMode thread mode, see [ThreadMode].
     * @param priority priority, bigger is higher.
     * @param tag optional tag that is useful for unsubscription, see [KEvent.removeSubscribersByTag].
     * @return true if subscription is successful, else false.
     */
    fun <T : Any> subscribe(
        eventType: Enum<*>,
        consumer: EventConsumer<T>,
        threadMode: ThreadMode = DEFAULT_THREAD_MODE,
        priority: Int = DEFAULT_PRIORITY,
        tag: String = DEFAULT_TAG
    ): Boolean {
        val subscriber = Subscriber(consumer, threadMode, priority, tag) as Subscriber<Any>
        val added = addSubscriber(eventType, subscriber)
        if (added) {
            synchronized(stickyEvents) {
                stickyEvents.filter { it.type == eventType }.forEach { event ->
                    dispatchEventAsync(event, subscriber)
                }
            }
        }
        return added
    }

    /**
     * Add a new subscriber with multiple [eventTypes]. Subscribers with the same [consumer] can only be added once.
     *
     * @param eventTypes event types that this subscriber subscribers.
     * @param consumer event consumer function.
     * @param threadMode thread mode, see [ThreadMode].
     * @param priority priority, bigger is higher.
     * @param tag optional tag that is useful for unsubscription, see [KEvent.removeSubscribersByTag].
     * @return true if subscription is successful, else false.
     */
    fun <T : Any> subscribeMultiple(
        eventTypes: Collection<Enum<*>>,
        threadMode: ThreadMode = DEFAULT_THREAD_MODE,
        priority: Int = DEFAULT_PRIORITY,
        tag: String = DEFAULT_TAG,
        consumer: EventConsumer<T>
    ): Boolean = subscribeMultiple(eventTypes, consumer, threadMode, priority, tag)


    /**
     * Add a new subscriber with multiple [eventTypes]. Subscribers with the same [consumer] can only be added once.
     *
     * @param eventTypes event types that this subscriber subscribers.
     * @param consumer event consumer function.
     * @param threadMode thread mode, see [ThreadMode].
     * @param priority priority, bigger is higher.
     * @param tag optional tag that is useful for unsubscription, see [KEvent.removeSubscribersByTag].
     * @return true if subscription is successful, else false.
     */
    fun <T : Any> subscribeMultiple(
        eventTypes: Collection<Enum<*>>,
        consumer: EventConsumer<T>,
        threadMode: ThreadMode = DEFAULT_THREAD_MODE,
        priority: Int = DEFAULT_PRIORITY,
        tag: String = DEFAULT_TAG
    ): Boolean {
        val subscriber = Subscriber(consumer, threadMode, priority, tag) as Subscriber<Any>
        var added = false
        val addedEventTypes = mutableSetOf<Enum<*>>()
        eventTypes.forEach { eventType ->
            if (addSubscriber(eventType, subscriber)) {
                added = true
                addedEventTypes.add(eventType)
            }
        }
        synchronized(stickyEvents) {
            stickyEvents.filter { it.type in addedEventTypes }.forEach { event ->
                dispatchEventAsync(event, subscriber)
            }
        }
        return added
    }

    /**
     * Unsubscribe the subscriber with [eventType] and [consumer].
     *
     * @return true if subscriber exists and unsubscription is successful, else false.
     */
    fun <T : Any> unsubscribe(eventType: Enum<*>, consumer: EventConsumer<T>): Boolean {
        subscribersMap[eventType]?.run {
            synchronized(this) {
                if (removeIf { subscriber -> subscriber.consumer == consumer }) {
                    updateSubscribersReadOnlyMap(eventType)
                    return true
                }
            }
        }
        return false
    }

    /**
     * Unsubscribe the subscriber with [eventTypes] and [consumer].
     *
     * @return true if subscriber exists and any of the unsubscription is successful, else false.
     */
    fun <T : Any> unsubscribeMultiple(eventTypes: Collection<Enum<*>>, consumer: EventConsumer<T>): Boolean {
        var removed = false
        eventTypes.forEach { eventType ->
            if (unsubscribe(eventType, consumer)) {
                removed = true
            }
        }
        return removed
    }

    /**
     * Block all events of type [eventType].
     *
     * @return true if [eventType] is not blocked yet and the blocking is successful, else false.
     */
    fun blockEventType(eventType: Enum<*>): Boolean {
        var applied = false
        synchronized(blockedEventTypeMap) {
            if (!blockedEventTypeMap.getOrDefault(eventType, false)) {
                blockedEventTypeMap[eventType] = true
                applied = true
            }
        }
        return applied
    }

    /**
     * Unblock all events of type [eventType].
     *
     * @return true if [eventType] is blocked yet and the unblocking is successful, else false.
     */
    fun unblockEventType(eventType: Enum<*>): Boolean {
        var applied = false
        synchronized(blockedEventTypeMap) {
            if (blockedEventTypeMap.getOrDefault(eventType, true)) {
                blockedEventTypeMap[eventType] = false
                applied = true
            }
        }
        return applied
    }

    /**
     * Unblock all event types.
     */
    fun unblockAllEventTypes() {
        blockedEventTypeMap.clear()
    }

    /**
     * Remove all subscribers with event type of [eventType].
     *
     * @return true if any subscriber gets removed, else false.
     */
    fun removeSubscribersByEventType(eventType: Enum<*>): Boolean {
        return if (subscribersMap.remove(eventType) != null) { updateSubscribersReadOnlyMap(eventType); true } else false
    }

    /**
     * Remove all subscribers with tag of [tag].
     *
     * @return true if any subscriber gets removed, else false.
     */
    fun removeSubscribersByTag(tag: String): Boolean {
        var removed = false
        subscribersMap.forEach { (eventType, subscribers) ->
            synchronized(subscribers) {
                if (subscribers.removeIf { it.tag == tag }) {
                    removed = true
                    updateSubscribersReadOnlyMap(eventType)
                }
            }
        }
        return removed
    }

    /**
     * Remove all subscribers.
     */
    fun removeAllSubscribers() {
        subscribersMap.clear()
        subscribersReadOnlyMap.clear()
    }

    /**
     * Clear all states of the [KEvent] object. All subscribers, sticky events, event blocking, etc will be removed.
     */
    fun clear() {
        removeAllSubscribers()
        removeAllStickyEvents()
        unblockAllEventTypes()
        cancelledEvents.clear()
    }
}