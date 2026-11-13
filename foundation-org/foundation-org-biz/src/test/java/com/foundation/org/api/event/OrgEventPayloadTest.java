package com.foundation.org.api.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** F-03 BAS-02 §6：四事件共用事件体只带 {@code id / version / changeType}，字段顺序稳定。 */
class OrgEventPayloadTest {

    @Test
    void payloadCarriesOnlyIdVersionAndChangeType() {
        Map<String, Object> payload = OrgChangedEvent.payload("org_1", 3L, OrgChangedEvent.CHANGE_MOVED).toMap();

        assertThat(payload).containsOnlyKeys("id", "version", "changeType");
        assertThat(payload.get("id")).isEqualTo("org_1");
        assertThat(payload.get("version")).isEqualTo(3L);
        assertThat(payload.get("changeType")).isEqualTo("MOVED");
    }

    @Test
    void fieldOrderIsStable() {
        assertThat(OrgChangedEvent.payload("org_1", 1L, OrgChangedEvent.CHANGE_CREATED).toMap().keySet())
                .containsExactly("id", "version", "changeType");
    }

    @Test
    void eventNamesMatchContract() {
        assertThat(OrgChangedEvent.EVENT_TYPE).isEqualTo("fdn.org.changed");
        assertThat(PersonCreatedEvent.EVENT_TYPE).isEqualTo("fdn.person.created");
        assertThat(PersonUpdatedEvent.EVENT_TYPE).isEqualTo("fdn.person.updated");
        assertThat(PersonDisabledEvent.EVENT_TYPE).isEqualTo("fdn.person.disabled");
    }

    @Test
    void personEventsForceTheirChangeType() {
        assertThat(PersonCreatedEvent.payload("per_1", 1L).changeType())
                .isEqualTo(PersonCreatedEvent.CHANGE_CREATED);
        assertThat(PersonUpdatedEvent.payload("per_1", 2L).changeType())
                .isEqualTo(PersonUpdatedEvent.CHANGE_UPDATED);
        assertThat(PersonDisabledEvent.payload("per_1", 3L).changeType())
                .isEqualTo(PersonDisabledEvent.CHANGE_DISABLED);
    }

    @Test
    void orgChangeTypesCoverCrudAndMove() {
        assertThat(OrgChangedEvent.CHANGE_CREATED).isEqualTo("CREATED");
        assertThat(OrgChangedEvent.CHANGE_UPDATED).isEqualTo("UPDATED");
        assertThat(OrgChangedEvent.CHANGE_MOVED).isEqualTo("MOVED");
        assertThat(OrgChangedEvent.CHANGE_DISABLED).isEqualTo("DISABLED");
    }
}
