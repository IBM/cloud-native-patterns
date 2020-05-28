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

import com.ibm.cnp.utils.ObjectUtils;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_API_VERSION;
import static com.ibm.cnp.samples.ICustomResourceCommons.CNP_CRD_GROUP;

public class JobFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobFactory.class);

    private static final String CNP_JOB_PLURAL_NAME = "cnpjobs";
    public static final String CNP_JOB_CRD_NAME = CNP_JOB_PLURAL_NAME + "." + CNP_CRD_GROUP;

    private final KubernetesClient client;
    private final CustomResourceDefinition crd;

    public JobFactory(KubernetesClient client) {
        /*
         * Save the client handle.
         */
        this.client = client;
        /*
         * Pre-register our CRD signature with the embedded JSON deserializer. This is a required step.
         *
         * See: fabric8io/kubernetes-client#1099
         */
        KubernetesDeserializer.registerCustomKind(CNP_API_VERSION, "Job", Job.class);
        /*
         * Look for the Job CRD.
         */
        this.crd = client.customResourceDefinitions()
                         .list()
                         .getItems()
                         .stream()
                         .filter(e -> e.getMetadata().getName().equals(CNP_JOB_CRD_NAME))
                         .findFirst()
                         .orElseThrow(RuntimeException::new);
    }

    public CustomResourceDefinition getCustomResourceDefinition() {
        return crd;
    }

    public void update(Job job, EJobState state) {
        /*
         * Duplicate the Job.
         */
        ObjectUtils.deepCopy(job, Job.class).ifPresent(target -> {
            /*
             * Update the width.
             */
            target.getSpec().setState(state);
            /*
             * Patch the Job.
             */
            LOGGER.debug("UPD - {}", job.getMetadata().getName());
            client.customResources(crd, Job.class, JobList.class, DoneableJob.class)
                  .inNamespace(job.getMetadata().getNamespace())
                  .withName(job.getMetadata().getName())
                  .patch(target);
        });
    }

}
