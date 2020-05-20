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

import com.ibm.cnp.events.IEventConsumerDelegate;
import com.ibm.cnp.samples.pod.PodStore;
import com.ibm.cnp.utils.OperationInProgressException;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.var;
import org.microbean.kubernetes.controller.AbstractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_JOB_LABEL_KEY;

public class JobConductor implements Runnable, IEventConsumerDelegate<HasMetadata> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobConductor.class);

    private final JobStore jobStore;
    private final IJobCoordinator jobCoordinator;
    private final PodStore podStore;

    private final AtomicBoolean keepRunning;
    private final HashSet<String> registry;
    private final LinkedBlockingQueue<AbstractEvent<? extends HasMetadata>> eventQueue;

    private final Thread thread;

    public JobConductor(JobStore jobStore, IJobCoordinator jobCoordinator, PodStore podStore) {
        /*
         * Save the stores and factories.
         */
        this.jobStore = jobStore;
        this.jobCoordinator = jobCoordinator;
        this.podStore = podStore;
        /*
         * Initialize the local state.
         */
        this.keepRunning = new AtomicBoolean(true);
        this.registry = new HashSet<>();
        this.eventQueue = new LinkedBlockingQueue<>();
        this.thread = new Thread(this);
    }

    private Optional<Job> getJobForResource(HasMetadata resource) {
        /*
         * If the resource is a job, return self.
         */
        if (resource instanceof Job) {
            return Optional.of((Job)resource);
        }
        /*
         * Grab the job name and check if it exists.
         */
        var name = resource.getMetadata().getLabels().get(CNP_JOB_LABEL_KEY);
        if (!registry.contains(name)) {
            LOGGER.trace("Job {} not registered in the FSM", name);
            return Optional.empty();
        }
        /*
         * Grab the job.
         */
        return jobStore.getJobWithName(name);
    }

    @Override
    public void run() {
        while (keepRunning.get()) {
            try {
                /*
                 * Grab a resource and process the timeouts.
                 */
                var event = eventQueue.poll(5, TimeUnit.SECONDS);
                if (event == null) {
                    continue;
                }
                /*
                 * Process the event.
                 */
                handle(event);
            } catch (InterruptedException | OperationInProgressException ignored) {
            }
        }
    }

    public void start() {
        this.thread.start();
    }

    public void close() throws InterruptedException {
        this.keepRunning.set(false);
        this.thread.join();
    }

    /*
     * Handle the event.
     */

    private void handle(AbstractEvent<? extends HasMetadata> event) throws OperationInProgressException {
        var cur = event.getResource();
        /*
         * Grab the job.
         */
        var job = getJobForResource(cur);
        if (!job.isPresent()) {
            return;
        }
        /*
         * Check the event type.
         */
        switch(event.getType()) {
            case ADDITION:
                if (cur instanceof Job) {
                    this.registry.add(((Job) cur).getMetadata().getName());
                } else if (cur instanceof Pod) {
                    process(job.get(), cur);
                }
            break;
            case MODIFICATION:
                var pre = event.getPriorResource();
                if (cur instanceof Job) {
                    if (((Job) cur).getSpec().getDesired() != ((Job)pre).getSpec().getDesired()) {
                        this.jobCoordinator.updateState(job.get(), EJobState.POD_CREATION);
                    }
                    process(job.get(), cur);
                }
                break;
            case DELETION:
                if (cur instanceof Job) {
                    this.registry.remove(((Job) cur).getMetadata().getName());
                } else if (cur instanceof Pod) {
                    this.jobCoordinator.updateState(job.get(), EJobState.POD_CREATION);
                    process(job.get(), cur);
                }
                break;
        }
    }

    /*
     * State operation methods.
     */

    private <T> void waitForCondition(Job job, Function<Job, T> cur, Supplier<T> tgt)
            throws OperationInProgressException {
        var value = cur.apply(job);
        var target = tgt.get();
        /*
         * Check the resource constraints.
         */
        if (!value.equals(target)) {
            throw new OperationInProgressException();
        }
    }

    /*
     * Job State Machine.
     */

    private void process(Job job, HasMetadata rsrc) throws OperationInProgressException {
        /*
         * Check if the job is in the right state.
         */
        if (job.getSpec().getState() == EJobState.READY) {
            return;
        }
        /*
         * Process the current state.
         */
        switch (job.getSpec().getState()) {
            case UNDEFINED:
                if (!(rsrc instanceof Job)) {
                    throw new OperationInProgressException();
                }
                var cur = (Job) rsrc;
                waitForCondition(cur, j -> j.getSpec().getState(), () -> EJobState.POD_CREATION);
                /*
                 * NOTE the fall-through is intended.
                 */
            case POD_CREATION:
                waitForCondition(job, j -> j.getSpec().getDesired(), podStore::size);
                jobCoordinator.updateState(job, EJobState.READY);
                /*
                 * NOTE the fall-through is intended.
                 */
            case READY:
                break;
        }
    }

    /*
     * Addition method.
     */

    @Override
    public void onAddition(AbstractEvent<? extends HasMetadata> event) {
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
     * Modification method.
     */

    @Override
    public void onModification(AbstractEvent<? extends HasMetadata> event) {
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
     * Deletion method.
     */

    @Override
    public void onDeletion(AbstractEvent<? extends HasMetadata> event) {
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
