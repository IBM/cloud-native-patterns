package com.ibm.cnp.samples.job;

import com.ibm.cnp.sync.Command;
import com.ibm.cnp.sync.Coordinator;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobCoordinator extends Coordinator<Job, JobCommandStatus> implements IJobCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobCoordinator.class);

    private final JobStore jobStore;
    private final JobFactory jobFactory;

    public JobCoordinator(JobStore jobStore, JobFactory jobFactory) {
        this.jobStore = jobStore;
        this.jobFactory = jobFactory;
    }

    private class UpdateStateCommand extends Command<Job, JobCommandStatus> {

        private final String jobName;
        private final EJobState state;

        UpdateStateCommand(Job job, EJobState state) {
            super(JobCommandStatus.Unknown);
            this.jobName = job.getMetadata().getName();
            this.state = state;
        }

        @Override
        public Action run() {
            /*
             * Find the job.
             */
            var job = jobStore.getJobWithName(jobName);
            if (!job.isPresent()) {
                LOGGER.debug("Job {} not found", jobName);
                set(JobCommandStatus.JobNotFound);
                return Action.Remove;
            }
            /*
             * Make sure the state needs an update.
             */
            if (job.get().getSpec().getState().equals(state)) {
                set(JobCommandStatus.NoChangeNeeded);
                return Action.Remove;
            }
            /*
             * Update the content ID and increase the launch count.
             */
            jobFactory.update(job.get(), state);
            return Action.Wait;
        }

        @Override
        public boolean check(Job pre, Job cur) {
            var result = cur.getSpec().getState().equals(this.state);
            set(result ? JobCommandStatus.Success : JobCommandStatus.Failure);
            return result;
        }

    }

    private void processStatus(JobCommandStatus status) {
        switch (status) {
            case Failure:
                throw new IllegalArgumentException("Operation failed");
            case JobNotFound:
                throw new IllegalArgumentException("Job not found");
            case NoChangeNeeded:
            case Success:
                break;
            case Unknown:
                throw new IllegalArgumentException("Invalid state");
        }
    }

    @Override
    public void updateState(Job job, EJobState state) {
        var cmd = new UpdateStateCommand(job, state);
        apply(job, cmd);
        processStatus(cmd.get());
    }

}

