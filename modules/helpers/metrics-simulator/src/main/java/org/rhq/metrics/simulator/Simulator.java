/*
 *
 *  * RHQ Management Platform
 *  * Copyright (C) 2005-2012 Red Hat, Inc.
 *  * All rights reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License, version 2, as
 *  * published by the Free Software Foundation, and/or the GNU Lesser
 *  * General Public License, version 2.1, also as published by the Free
 *  * Software Foundation.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License and the GNU Lesser General Public License
 *  * for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * and the GNU Lesser General Public License along with this program;
 *  * if not, write to the Free Software Foundation, Inc.,
 *  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package org.rhq.metrics.simulator;

import static com.datastax.driver.core.ProtocolOptions.Compression.SNAPPY;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleAuthInfoProvider;
import com.datastax.driver.core.exceptions.NoHostAvailableException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.cassandra.CassandraClusterManager;
import org.rhq.cassandra.CassandraNode;
import org.rhq.cassandra.ClusterInitService;
import org.rhq.cassandra.DeploymentOptions;
import org.rhq.cassandra.schema.SchemaManager;
import org.rhq.metrics.simulator.plan.ScheduleGroup;
import org.rhq.metrics.simulator.plan.SimulationPlan;
import org.rhq.metrics.simulator.plan.SimulationPlanner;
import org.rhq.server.metrics.DateTimeService;
import org.rhq.server.metrics.MetricsServer;

/**
 * @author John Sanda
 */
public class Simulator {

    private final Log log = LogFactory.getLog(Simulator.class);

    public void run(File jsonFile) throws Exception {
        SimulationPlanner planner = new SimulationPlanner();
        SimulationPlan plan = planner.create(jsonFile);

        List<CassandraNode> nodes = deployCluster();
        waitForClusterToInitialize(nodes);

        createSchema(nodes);

        Session session = createSession();

        MetricsServer metricsServer = new MetricsServer();
        metricsServer.setSession(session);
        metricsServer.setConfiguration(plan.getMetricsServerConfiguration());

        DateTimeService dateTimeService = new DateTimeService();
        dateTimeService.setConfiguration(plan.getMetricsServerConfiguration());
        metricsServer.setDateTimeService(dateTimeService);

        Set<Schedule> schedules = initSchedules(plan.getScheduleSets().get(0));
        PriorityQueue<Schedule> queue = new PriorityQueue<Schedule>(schedules);
        ReentrantLock queueLock = new ReentrantLock();

        MeasurementAggregator measurementAggregator = new MeasurementAggregator();
        measurementAggregator.setMetricsServer(metricsServer);

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(plan.getThreadPoolSize());
        log.info("Starting executor service");
        for (int i = 0; i < plan.getNumMeasurementCollectors(); ++i) {
            MeasurementCollector measurementCollector = new MeasurementCollector();
            measurementCollector.setMetricsServer(metricsServer);
            measurementCollector.setQueue(queue);
            measurementCollector.setQueueLock(queueLock);

            executorService.scheduleAtFixedRate(measurementCollector, 0, plan.getCollectionInterval(),
                TimeUnit.MILLISECONDS);
        }

        executorService.scheduleAtFixedRate(measurementAggregator, 0, plan.getAggregationInterval(),
            TimeUnit.MILLISECONDS);

        try {
            Thread.sleep(10000 * 6 * 20);
        } catch (InterruptedException e) {
        }
        log.info("Shutting down executor service");
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    private List<CassandraNode> deployCluster() throws IOException {
        File clusterDir = new File("target/cassandra");
        log.info("Deploying cluster to " + clusterDir);
        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setClusterDir(clusterDir.getAbsolutePath());
        deploymentOptions.setNumNodes(2);
        deploymentOptions.setHeapSize("64M");
        deploymentOptions.setHeapNewSize("8M");
        deploymentOptions.setLoggingLevel("INFO");
        deploymentOptions.load();

        CassandraClusterManager ccm = new CassandraClusterManager(deploymentOptions);
        List<CassandraNode> nodes = ccm.createCluster();
        ccm.startCluster();

        return nodes;
    }

    private void waitForClusterToInitialize(List<CassandraNode> nodes) {
        log.info("Waiting for cluster to initialize");
        ClusterInitService clusterInitService = new ClusterInitService();
        clusterInitService.waitForClusterToStart(nodes);
    }

    private void createSchema(List<CassandraNode> nodes) {
        log.info("Creating schema");
        SchemaManager schemaManager = new SchemaManager("rhqadmin", "rhqadmin", nodes);
        schemaManager.createSchema();
        schemaManager.updateSchema();
    }

    private Session createSession() throws NoHostAvailableException {
        SimpleAuthInfoProvider authInfoProvider = new SimpleAuthInfoProvider();
        authInfoProvider.add("username", "rhqadmin").add("password", "rhqadmin");

        Cluster cluster = Cluster.builder()
            .addContactPoints("127.0.0.1", "127.0.0.2")
            .withAuthInfoProvider(authInfoProvider)
            .withCompression(SNAPPY)
            .build();

        return cluster.connect("rhq");
    }

    private Set<Schedule> initSchedules(ScheduleGroup scheduleSet) {
        long nextCollection = System.currentTimeMillis();
        Set<Schedule> schedules = new HashSet<Schedule>();
        for (int i = 0; i < scheduleSet.getCount(); ++i) {
            Schedule schedule = new Schedule(i);
            schedule.setInterval(scheduleSet.getInterval());
            schedule.setNextCollection(nextCollection);
            schedules.add(schedule);
        }
        return schedules;
    }

}
