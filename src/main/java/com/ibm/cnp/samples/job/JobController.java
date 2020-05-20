/*
 * Copyright 2020 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.cnp.samples.job;

import com.ibm.cnp.events.GenericEventQueueConsumer;
import com.ibm.cnp.samples.pod.PodFactory;
import com.ibm.cnp.samples.pod.PodStore;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.var;
import org.microbean.kubernetes.controller.AbstractEvent;
import org.microbean.kubernetes.controller.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.stream.IntStream;

public class JobController extends GenericEventQueueConsumer<Job> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobController.class);

    private final JobFactory jobFactory;
    private final PodStore podStore;
    private final PodFactory podFactory;
    private final Controller<Job> controller;

    public JobController(KubernetesClient client, JobStore jobStore, JobFactory jobFactory, PodStore podStore,
                         PodFactory podFactory, String ns) {
        super(jobStore);
        this.jobFactory = jobFactory;
        this.podStore = podStore;
        this.podFactory = podFactory;
        this.controller = new Controller<>(
                client.customResources(jobFactory.getCustomResourceDefinition(), Job.class, JobList.class,
                                       DoneableJob.class).inNamespace(ns), this);
    }

    @Override
    public void onAddition(AbstractEvent<? extends Job> event) {
        LOGGER.info("ADD - {}", event.getResource().getMetadata().getName());
        jobFactory.update(event.getResource(), EJobState.POD_CREATION);
    }

    @Override
    public void onModification(AbstractEvent<? extends Job> event) {
        var pre = event.getPriorResource();
        var cur = event.getResource();
        /*
         * Return if pre and cur have the same state.
         */
        if (pre.getSpec().getState() == cur.getSpec().getState()) {
            return;
        }
        LOGGER.info("MOD - {}", cur.getMetadata().getName());
        /*
         * Check the state transition.
         */
        switch (cur.getSpec().getState()) {
            case UNDEFINED:
                LOGGER.error("Invalid state transition to UNDEFINED");
                break;
            case POD_CREATION:
                var pods = this.podStore.getPodsForJob(cur).toArray();
                int delta = cur.getSpec().getDesired() - pods.length;
                if (delta < 0) {
                    LOGGER.debug("Delete {} pod(s)", -delta);
                    for (int i = pods.length + delta; i < pods.length; i += 1) {
                        podFactory.delete((Pod) pods[i]);
                    }
                } else if (delta > 0) {
                    LOGGER.debug("Add {} pod(s)", delta);
                    IntStream.range(0, delta).forEach(i -> podFactory.add(cur));
                }
                break;
            case READY:
                LOGGER.debug("Job is READY");
                break;
        }
    }

    @Override
    public void onDeletion(AbstractEvent<? extends Job> event) {
        LOGGER.info("DEL - {}", event.getResource().getMetadata().getName());
    }

    public void start() throws IOException {
        controller.start();
    }

    public void close() throws IOException {
        controller.close();
    }
}
