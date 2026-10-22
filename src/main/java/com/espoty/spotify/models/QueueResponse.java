package com.espoty.spotify.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueueResponse {
    private final QueueItem currentlyPlaying;
    private final List<QueueItem> queue;

    public QueueResponse(QueueItem currentlyPlaying, List<QueueItem> queue) {
        this.currentlyPlaying = currentlyPlaying;
        this.queue = queue != null ? new ArrayList<>(queue) : Collections.emptyList();
    }

    public QueueItem getCurrentlyPlaying() {
        return currentlyPlaying;
    }

    public List<QueueItem> getQueue() {
        return Collections.unmodifiableList(queue);
    }
}
