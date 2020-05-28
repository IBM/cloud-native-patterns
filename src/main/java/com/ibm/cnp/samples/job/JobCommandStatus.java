package com.ibm.cnp.samples.job;

import com.ibm.cnp.sync.ICommandStatus;

enum JobCommandStatus implements ICommandStatus {
    Failure,
    JobNotFound,
    NoChangeNeeded,
    Success,
    Unknown;

    @Override
    public boolean isUnknown() {
        return this == Unknown;
    }
}
