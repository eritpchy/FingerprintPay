package com.surcumference.fingerprint.util;

import java.lang.ref.WeakReference;

public class PaymentSession {
    private WeakReference<Object> owner = new WeakReference<>(null);
    private WeakReference<Object> target = new WeakReference<>(null);
    private long token;
    private long finishedAt = -1;
    private boolean active;
    private boolean authenticating;

    public boolean begin(Object owner, Object target, long now) {
        if (owner == this.owner.get() && (target == this.target.get() || active
                || (finishedAt >= 0 && now - finishedAt < 3000))) {
            return false;
        }
        restart(owner, target);
        return true;
    }

    public void restart(Object owner, Object target) {
        this.owner = new WeakReference<>(owner);
        this.target = new WeakReference<>(target);
        ++token;
        active = true;
        authenticating = true;
        finishedAt = -1;
    }

    public long token() {
        return token;
    }

    public boolean owns(long token) {
        return this.token == token && owner.get() != null && target.get() != null;
    }

    public boolean isActive(long token) {
        return owns(token) && active;
    }

    public boolean isAuthenticating(Object owner) {
        return this.owner.get() == owner && active && authenticating;
    }

    public boolean authenticated(long token) {
        if (!isActive(token) || !authenticating) {
            return false;
        }
        authenticating = false;
        return true;
    }

    public boolean finish(long token, long now) {
        if (!owns(token) || !active) {
            return false;
        }
        active = false;
        authenticating = false;
        finishedAt = now;
        return true;
    }

    public Object owner() {
        return owner.get();
    }

    public Object target() {
        return target.get();
    }

    public void close(Object owner) {
        if (this.owner.get() != owner) {
            return;
        }
        ++token;
        active = false;
        authenticating = false;
        this.owner.clear();
        target.clear();
        finishedAt = -1;
    }
}
