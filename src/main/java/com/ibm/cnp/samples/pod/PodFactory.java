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

import com.ibm.cnp.samples.job.Job;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.var;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;

import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_APP_LABEL_KEY;
import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_APP_LABEL_VALUE;
import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_CRD_GROUP;
import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_CRD_VERSION;
import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_JOB_LABEL_KEY;

public class PodFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PodFactory.class);

    private final KubernetesClient client;
    private final PodStore podStore;

    public PodFactory(KubernetesClient client, PodStore podStore) {
        this.client = client;
        this.podStore = podStore;
    }

    private boolean isUnique(String name) {
        return podStore.values().stream().noneMatch(pod -> pod.getMetadata().getName().equals(name));
    }

    private String getUniquePodName(Job job) {
        String podName;
        do {
            var suffix = RandomStringUtils.random(5, true, true).toLowerCase();
            podName = job.getMetadata().getName() + "-" + suffix;
        } while (!isUnique(podName));
        return podName;
    }

    public void add(Job job) {
        /*
         * Allocate a unique pod name.
         */
        var name = getUniquePodName(job);
        /*
         * Create the spec.
         */
        var container = new Container();
        container.setName("main");
        container.setImage(job.getSpec().getImage());
        container.setCommand(job.getSpec().getArgs());
        var spec = new PodSpec();
        spec.setContainers(Collections.singletonList(container));
        spec.setRestartPolicy("Never");
        /*
         * Build owner reference.
         */
        var or = new OwnerReference();
        or.setApiVersion(CNP_CRD_GROUP + '/' + CNP_CRD_VERSION);
        or.setKind("Job");
        or.setName(job.getMetadata().getName());
        or.setUid(job.getMetadata().getUid());
        or.setController(true);
        or.setBlockOwnerDeletion(true);
        /*
         * Build the labels.
         */
        var labels = new HashMap<String, String>() {{
            put(CNP_APP_LABEL_KEY, CNP_APP_LABEL_VALUE);
            put(CNP_JOB_LABEL_KEY, job.getMetadata().getName());
        }};
        /*
         * Build the pod metadata.
         */
        var meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(job.getMetadata().getNamespace());
        meta.setOwnerReferences(Collections.singletonList(or));
        meta.setLabels(labels);
        /*
         * Build the pod.
         */
        var pod = new Pod();
        pod.setMetadata(meta);
        pod.setSpec(spec);
        /*
         * Create the pod.
         */
        client.pods().inNamespace(job.getMetadata().getNamespace()).create(pod);
    }

    public void delete(Pod pod) {
        this.client.pods().delete(pod);
    }

}
