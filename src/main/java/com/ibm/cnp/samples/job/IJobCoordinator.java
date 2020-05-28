package com.ibm.cnp.samples.job;

import com.ibm.cnp.events.IEventConsumerDelegate;

public interface IJobCoordinator extends IEventConsumerDelegate<Job> {

    void updateState(Job job, EJobState state);

}
