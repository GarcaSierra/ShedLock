/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.reactive.mongo;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber that expects a single result and allows locking until complete or error
 *
 * @param <T>
 */
class SingleLockableSubscriber<T> implements Subscriber<T> {

    private T value;
    private Throwable error;
    private boolean complete = false;
    private static final Long TIMEOUT_MILLIS = 10000L;
    private static final Long LOCK_TIME = 100L;

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(1);
    }

    @Override
    public void onNext(T document) {
        value = document;
    }

    @Override
    public void onError(Throwable throwable) {
        this.error = throwable;
    }

    @Override
    public void onComplete() {
        complete = true;
    }

    T getValue() {
        return value;
    }

    Throwable getError() {
        return error;
    }

    boolean isComplete() {
        return complete;
    }

    void waitUntilCompleteOrError() {
        long waitTime = 0;
        while (waitTime <= TIMEOUT_MILLIS && !this.isComplete() && this.getError() == null) {
            try {
                Thread.sleep(LOCK_TIME);
                waitTime += LOCK_TIME;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
