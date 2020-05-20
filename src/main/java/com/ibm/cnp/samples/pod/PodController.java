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
package com.ibm.cnp.samples.pod;

import com.ibm.cnp.events.GenericEventQueueConsumer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.microbean.kubernetes.controller.AbstractEvent;
import org.microbean.kubernetes.controller.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PodController extends GenericEventQueueConsumer<Pod> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PodController.class);

    private final Controller<Pod> controller;

    public PodController(KubernetesClient client, PodStore podStore, String ns) {
        super(podStore);
        controller = new Controller<>(client.pods().inNamespace(ns), this);
    }

    @Override
    public void onAddition(AbstractEvent<? extends Pod> event) {
        LOGGER.info("ADD - {}", event.getResource().getMetadata().getName());
    }

    @Override
    public void onModification(AbstractEvent<? extends Pod> event) {
        LOGGER.info("MOD - {}", event.getResource().getMetadata().getName());
    }

    @Override
    public void onDeletion(AbstractEvent<? extends Pod> event) {
        LOGGER.info("DEL - {}", event.getResource().getMetadata().getName());
    }

    public void start() throws IOException {
        controller.start();
    }

    public void close() throws IOException {
        controller.close();
    }

}
