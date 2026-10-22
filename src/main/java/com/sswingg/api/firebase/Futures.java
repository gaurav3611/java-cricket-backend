package com.sswingg.api.firebase;

import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;

/** Waits for a Firestore call and turns its failure into an unchecked exception. */
final class Futures {

    private Futures() {
    }

    static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Firestore", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }
}
