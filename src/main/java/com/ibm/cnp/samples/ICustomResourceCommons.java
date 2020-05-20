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

public interface ICustomResourceCommons {

    String CNP_CRD_GROUP = "cnp.ibm.com";
    String CNP_CRD_VERSION = "v1";

    static String GROUP(String name) {
        return CNP_CRD_GROUP + "/" + name;
    }

    String CNP_API_VERSION = GROUP(CNP_CRD_VERSION);

    String CNP_APP_LABEL_KEY = "app";
    String CNP_APP_LABEL_VALUE = "cnp";

    String CNP_JOB_LABEL_KEY = GROUP("job");
}
