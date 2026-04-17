package me.cookie.duel.config.model;

import java.util.Map;

public record QueuesConfig(Map<String, QueueDefinition> queues) {
}
