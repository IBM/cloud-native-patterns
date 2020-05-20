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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@JsonDeserialize
public class JobSpec implements KubernetesResource {

    @Getter @Setter private EJobState state;
    @Getter @Setter private int desired;
    @Getter @Setter private String image;
    @Getter @Setter private List<String> args;

    public JobSpec() {
        this.state = EJobState.UNDEFINED;
        this.desired = 0;
    }

    public JobSpec(JobSpec spec) {
        this.state = spec.state;
        this.desired = spec.desired;
        this.image = spec.image;
        this.args = new ArrayList<>(spec.args);
    }
}
