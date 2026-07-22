package org.folio.notes.support;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.awaitility.Awaitility.await;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

/**
 * Reusable test consumer that subscribes to a single Kafka topic with a raw {@code String} value deserializer and
 * buffers every received record. Integration tests use it to assert on the JSON payload of published domain events.
 *
 * <p>The consumer owns its listener container and the record buffer; callers only need to
 * {@link #subscribe(String, KafkaProperties)} it, {@link #poll(Duration)} the buffered records, and {@link #close()}
 * it when done (it is {@link Closeable}, so it also works with try-with-resources or an {@code @AfterEach} hook).</p>
 */
public final class TestKafkaConsumer implements Closeable {

  private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

  private final KafkaMessageListenerContainer<String, String> container;
  private final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

  private TestKafkaConsumer(KafkaMessageListenerContainer<String, String> container) {
    this.container = container;
  }

  /**
   * Creates and starts a consumer subscribed to the given topic.
   *
   * @param topic      the topic to consume from (already env/tenant qualified)
   * @param properties Spring Kafka properties (bootstrap servers point at the embedded broker)
   * @return a started consumer; close it when done
   */
  public static TestKafkaConsumer subscribe(String topic, KafkaProperties properties) {
    createTopic(topic, properties);
    properties.getConsumer().setGroupId("mod-notes-test-group");
    properties.getConsumer().setAutoOffsetReset("earliest");
    Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    var consumerFactory = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
    var containerProperties = new ContainerProperties(topic);
    var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    var consumer = new TestKafkaConsumer(container);
    container.setupMessageListener((MessageListener<String, String>) consumer.records::add);
    container.start();
    return consumer;
  }

  /**
   * Eagerly creates the topic via an admin client so the producer does not race the broker's lazy auto-creation
   * (which otherwise surfaces as {@code Topic ... not present in metadata} on the first send).
   *
   * @param topic      the topic to create (no-op if it already exists)
   * @param properties Spring Kafka properties (used for the bootstrap servers)
   */
  private static void createTopic(String topic, KafkaProperties properties) {
    try (var admin = Admin.create(properties.buildAdminProperties())) {
      admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof TopicExistsException)) {
        throw new IllegalStateException("Failed to create test topic " + topic, e);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while creating test topic " + topic, e);
    }
  }

  /**
   * Waits (up to one minute) for the next record and returns it.
   *
   * @return the received record
   */
  public ConsumerRecord<String, String> poll(String key) {
    return poll(DEFAULT_POLL_TIMEOUT).stream()
      .filter(e -> Objects.equals(e.key(), key)).findFirst()
      .orElse(null);
  }

  /**
   * Waits up to {@code timeout} for the next record and returns it, failing the calling test if none arrives.
   *
   * @param timeout the maximum time to wait
   * @return the received record
   */
  public List<ConsumerRecord<String, String>> poll(Duration timeout) {
    var holder = new AtomicReference<List<ConsumerRecord<String, String>>>();
    await().pollInterval(POLL_INTERVAL).atMost(timeout)
      .untilAsserted(() -> {
        List<ConsumerRecord<String, String>> events = new ArrayList<>();
        records.drainTo(events);
        holder.set(events);
      });
    return holder.get();
  }

  @Override
  public void close() {
    container.stop();
  }
}



