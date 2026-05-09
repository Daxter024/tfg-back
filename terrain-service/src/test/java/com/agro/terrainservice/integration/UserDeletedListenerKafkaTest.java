package com.agro.terrainservice.integration;

import com.agro.terrainservice.event.UserDeletedEvent;
import com.agro.terrainservice.listener.UserDeletedListener;
import com.agro.terrainservice.service.TerrainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Integracion Kafka del listener {@code user-deleted} con un broker embebido
 * (EmbeddedKafka). Cubre TER-11.02 (mensaje despachado, listener delega al
 * service) y TER-11.06 (idempotencia delegada).
 *
 * <p>El contexto se aisla excluyendo la auto-configuracion de JPA / Flyway /
 * DataSource para no necesitar BBDD. Solo cargamos {@link UserDeletedListener}
 * y mockeamos {@link TerrainService}.</p>
 */
@SpringBootTest(classes = UserDeletedListenerKafkaTest.KafkaTestApp.class)
@EmbeddedKafka(partitions = 1, topics = {"user-deleted"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=terrain-service-group-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.agro.terrainservice.event.UserDeletedEvent",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
})
class UserDeletedListenerKafkaTest {

    @MockitoBean
    private TerrainService terrainService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("TER-11.02 - listener consume UserDeletedEvent y delega a TerrainService")
    void listener_consumesAndDispatches() {
        UUID uid = UUID.randomUUID();
        UserDeletedEvent event = new UserDeletedEvent(uid);

        kafkaTemplate.send("user-deleted", uid.toString(), event);

        verify(terrainService, timeout(20_000).times(1)).deleteTerrainsByUserId(uid);
    }

    @Test
    @DisplayName("TER-11.06 - dos eventos identicos se procesan ambos (idempotencia delegada)")
    void listener_processesIdenticalEventsTwice() {
        UUID uid = UUID.randomUUID();
        UserDeletedEvent event = new UserDeletedEvent(uid);

        kafkaTemplate.send("user-deleted", uid.toString(), event);
        kafkaTemplate.send("user-deleted", uid.toString(), event);

        verify(terrainService, timeout(20_000).times(2)).deleteTerrainsByUserId(uid);
    }

    /**
     * App de test minima: solo importa lo necesario para el listener Kafka, y
     * excluye explícitamente la autoconfig de JPA / Flyway / DataSource para
     * no necesitar BBDD.
     */
    @SpringBootConfiguration
    @EnableKafka
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Configuration
    static class KafkaTestApp {

        @Bean
        public UserDeletedListener userDeletedListener(TerrainService ts) {
            return new UserDeletedListener(ts);
        }

        @Bean
        public ProducerFactory<String, Object> producerFactory(
                @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
                        String brokers) {
            Map<String, Object> props = new HashMap<>();
            props.put(BOOTSTRAP_SERVERS_CONFIG, brokers);
            props.put(KEY_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            props.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
            return new KafkaTemplate<>(pf);
        }
    }
}
