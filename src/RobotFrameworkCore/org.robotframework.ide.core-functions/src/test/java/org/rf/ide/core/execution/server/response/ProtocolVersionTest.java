/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.execution.server.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ProtocolVersionTest {

    @Test
    public void properMessageIsConstructed_forProtocolVersionMessage() {
        assertThat(new ProtocolVersion(true).toMessage()).isEqualTo("{\"protocol_version\":{\"is_correct\":true}}");
        assertThat(new ProtocolVersion(false).toMessage()).isEqualTo("{\"protocol_version\":{\"is_correct\":false}}");
    }
}
