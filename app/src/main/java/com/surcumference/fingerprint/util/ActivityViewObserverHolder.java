package com.surcumference.fingerprint.util;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

public final class ActivityViewObserverHolder {
    public enum Key {
        TaobaoPasswordView,
        WeChatPayView, UnionPayPasswordView,
    }
    private static final Map<Key, Registration> HOLDER = new EnumMap<>(Key.class);
    private static final class Registration {
        final ActivityViewObserver observer;
        Task.Handle timeout;
        long generation;
        Registration(ActivityViewObserver observer) { this.observer = observer; }
        void stop() {
            if (timeout != null) timeout.cancel();
            observer.stop();
        }
    }
    public static void stop(Key key) {
        Task.runOnMain(() -> {
            Registration registration = HOLDER.remove(key);
            if (registration != null) registration.stop();
        });
    }
    public static void stop(ActivityViewObserver observer) {
        Task.runOnMain(() -> {
            observer.stop();
            Iterator<Registration> iterator = HOLDER.values().iterator();
            while (iterator.hasNext()) {
                Registration registration = iterator.next();
                if (registration.observer == observer) {
                    iterator.remove();
                    registration.stop();
                }
            }
        });
    }
    public static void start(Key key, ActivityViewObserver observer, long loop,
                             ActivityViewObserver.IActivityViewListener listener) {
        start(key, observer, loop, listener, 0);
    }
    public static void start(Key key, ActivityViewObserver observer, long loop,
                             ActivityViewObserver.IActivityViewListener listener, long timeout) {
        Task.runOnMain(() -> {
            stop(key);
            Registration registration = new Registration(observer);
            HOLDER.put(key, registration);
            if (timeout > 0) registration.timeout = Task.onMain(timeout, () -> {
                if (HOLDER.get(key) == registration && observer.generation() == registration.generation) stop(key);
            });
            observer.start(loop, listener);
            registration.generation = observer.generation();
        });
    }
}
