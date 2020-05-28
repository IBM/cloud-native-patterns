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
package com.ibm.cnp.samples;

import com.ibm.cnp.samples.job.JobConductor;
import com.ibm.cnp.samples.job.JobController;
import com.ibm.cnp.samples.job.JobCoordinator;
import com.ibm.cnp.samples.job.JobFactory;
import com.ibm.cnp.samples.job.JobStore;
import com.ibm.cnp.samples.pod.PodController;
import com.ibm.cnp.samples.pod.PodFactory;
import com.ibm.cnp.samples.pod.PodStore;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.var;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    private static final Lock lock = new ReentrantLock();
    private static final Condition terminated = lock.newCondition();

    public static void main(String[] args) {
        /*
         * Register a shutdown hook.
         */
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            lock.lock();
            terminated.signal();
            lock.unlock();
        }));
        /*
         * Send JUL log events to SLF4J.
         */
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        /*
         * Set the JUL loggers' to the minimum verbosity.
         */
        var juls = java.util.logging.LogManager.getLogManager().getLoggerNames();
        while (juls.hasMoreElements()) {
            var logger = java.util.logging.Logger.getLogger(juls.nextElement());
            logger.setLevel(java.util.logging.Level.OFF);
        }
        /*
         * Main try block.
         */
        try {
            /*
             * Grab the controller namespace and pod name environment variable.
             */
            var ns = Optional.ofNullable(System.getenv("POD_NAMESPACE")).orElse("default");
            var logLevel = Optional.ofNullable(System.getenv("LOG_LEVEL")).orElse("DEBUG");
            /*
             * Grab a new Kube client.
             */
            var client = new DefaultKubernetesClient();
            /*
             * Update the loggers.
             */
            var loggers = LogManager.getCurrentLoggers();
            while(loggers.hasMoreElements()) {
                Logger logger = (Logger) loggers.nextElement();
                logger.setLevel(Level.toLevel(logLevel));
            }
            /*
             * Create the pod controller.
             */
            var podStore = new PodStore();
            var podFactory = new PodFactory(client, podStore);
            var podController = new PodController(client, podStore, ns);
            /*
             * Create the job controller.
             */
            var jobStore = new JobStore();
            var jobFactory = new JobFactory(client);
            var jobController = new JobController(client, jobStore, jobFactory, podStore, podFactory, ns);
            /*
             * Create the job coordinator.
             */
            var jobCoordinator = new JobCoordinator(jobStore, jobFactory);
            jobController.addListener(jobCoordinator);
            /*
             * Create the job conductor.
             */
            var jobConductor = new JobConductor(jobStore, jobCoordinator, podStore);
            jobController.addGenericListener(jobConductor);
            podController.addGenericListener(jobConductor);
            /*
             * Start the Job FSM and the controllers
             */
            lock.lock();
            podController.start();
            jobController.start();
            jobConductor.start();
            terminated.await();
            lock.unlock();
            /*
             * Close the controllers upon termination.
             */
            jobConductor.close();
            jobController.close();
            podController.close();
        } catch (IOException | KubernetesClientException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
