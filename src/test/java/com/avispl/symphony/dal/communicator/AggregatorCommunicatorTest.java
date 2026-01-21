/*
 * Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator;

import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;

public class AggregatorCommunicatorTest {

    OwlLabsAggregatorCommunicator communicator;

    @BeforeEach
    public void setUp() throws Exception {
        communicator = new OwlLabsAggregatorCommunicator();
        communicator.setHost("api.dev.owllabs.com");
        communicator.setOauthHostname("auth.api.dev.owllabs.com");
        communicator.setLogin("s12n56tq7m3n81e6t80rhpmfo");
        communicator.setPassword("qudpjc1j5islghs96840od5s3khhkgcsk5c97euk9smbcop9jtu");
        communicator.init();
    }

    @Test
    public void testGetMultupleStatistics() throws Exception {
        List<Statistics> statistics = communicator.getMultipleStatistics();
        Assertions.assertNotNull(statistics);
    }

    @Test
    public void testRetrieveMultupleStatistics() throws Exception {
        List<AggregatedDevice> statistics = communicator.retrieveMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        Assertions.assertNotNull(statistics);
    }
}
